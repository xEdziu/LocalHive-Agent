package dev.adrian.goral.localhiveagent.security;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

final class NativeMacOsKeychainBackend implements MacOsKeychainBackend {

    private static final int ERR_SEC_SUCCESS = 0;
    private static final int ERR_SEC_ITEM_NOT_FOUND = -25300;

    private static final String AVAILABILITY_SERVICE_NAME =
            "dev.adrian.goral.localhiveagent.keychain-availability";
    private static final String AVAILABILITY_ACCOUNT_NAME = "availability-probe";

    private volatile SecurityFramework securityFramework;
    private volatile CoreFoundation coreFoundation;

    NativeMacOsKeychainBackend() {
        this(null, null);
    }

    NativeMacOsKeychainBackend(SecurityFramework securityFramework, CoreFoundation coreFoundation) {
        this.securityFramework = securityFramework;
        this.coreFoundation = coreFoundation;
    }

    @Override
    public boolean isAvailable() {
        try (KeychainLookup lookup = findGenericPassword(
                AVAILABILITY_SERVICE_NAME,
                AVAILABILITY_ACCOUNT_NAME,
                false
        )) {
            return lookup.status() == ERR_SEC_SUCCESS
                    || lookup.status() == ERR_SEC_ITEM_NOT_FOUND;
        }
    }

    @Override
    public Optional<String> loadApiKey(String serviceName, String accountName) {
        try (KeychainLookup lookup = findGenericPassword(serviceName, accountName, true)) {
            if (lookup.status() == ERR_SEC_ITEM_NOT_FOUND) {
                return Optional.empty();
            }

            ensureSuccess(lookup.status(), "read");

            byte[] passwordBytes = lookup.passwordBytes();

            if (passwordBytes.length == 0) {
                return Optional.empty();
            }

            return Optional.of(new String(passwordBytes, StandardCharsets.UTF_8));
        }
    }

    @Override
    public void saveApiKey(String serviceName, String accountName, String apiKey) {
        byte[] passwordBytes = apiKey.getBytes(StandardCharsets.UTF_8);

        try {
            try (KeychainLookup lookup = findGenericPassword(serviceName, accountName, false)) {
                if (lookup.status() == ERR_SEC_ITEM_NOT_FOUND) {
                    addGenericPassword(serviceName, accountName, passwordBytes);
                    return;
                }

                ensureSuccess(lookup.status(), "find existing item for update");
                modifyGenericPassword(lookup.itemRef(), passwordBytes);
            }
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    @Override
    public void deleteApiKey(String serviceName, String accountName) {
        try (KeychainLookup lookup = findGenericPassword(serviceName, accountName, false)) {
            if (lookup.status() == ERR_SEC_ITEM_NOT_FOUND) {
                return;
            }

            ensureSuccess(lookup.status(), "find item for deletion");

            int deleteStatus = securityFramework().SecKeychainItemDelete(lookup.itemRef());
            ensureSuccess(deleteStatus, "delete");
        }
    }

    private void addGenericPassword(String serviceName, String accountName, byte[] passwordBytes) {
        byte[] serviceNameBytes = utf8Bytes(serviceName);
        byte[] accountNameBytes = utf8Bytes(accountName);
        PointerByReference itemRef = new PointerByReference();

        int status = securityFramework().SecKeychainAddGenericPassword(
                Pointer.NULL,
                serviceNameBytes.length,
                serviceNameBytes,
                accountNameBytes.length,
                accountNameBytes,
                passwordBytes.length,
                passwordBytes,
                itemRef
        );

        Pointer createdItemRef = itemRef.getValue();

        if (createdItemRef != null) {
            coreFoundation().CFRelease(createdItemRef);
        }

        ensureSuccess(status, "save");
    }

    private void modifyGenericPassword(Pointer itemRef, byte[] passwordBytes) {
        int status = securityFramework().SecKeychainItemModifyAttributesAndData(
                itemRef,
                Pointer.NULL,
                passwordBytes.length,
                passwordBytes
        );

        ensureSuccess(status, "update");
    }

    private KeychainLookup findGenericPassword(
            String serviceName,
            String accountName,
            boolean includePassword
    ) {
        byte[] serviceNameBytes = utf8Bytes(serviceName);
        byte[] accountNameBytes = utf8Bytes(accountName);
        IntByReference passwordLength = includePassword ? new IntByReference() : null;
        PointerByReference passwordData = includePassword ? new PointerByReference() : null;
        PointerByReference itemRef = new PointerByReference();

        int status = securityFramework().SecKeychainFindGenericPassword(
                Pointer.NULL,
                serviceNameBytes.length,
                serviceNameBytes,
                accountNameBytes.length,
                accountNameBytes,
                passwordLength,
                passwordData,
                itemRef
        );

        return new KeychainLookup(
                status,
                passwordLength == null ? 0 : passwordLength.getValue(),
                passwordData == null ? null : passwordData.getValue(),
                itemRef.getValue()
        );
    }

    private static byte[] utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void ensureSuccess(int status, String operation) {
        if (status == ERR_SEC_SUCCESS) {
            return;
        }

        throw new CredentialException(
                "macOS Keychain " + operation + " operation failed. Status code: " + status + "."
        );
    }

    private SecurityFramework securityFramework() {
        SecurityFramework currentFramework = securityFramework;

        if (currentFramework != null) {
            return currentFramework;
        }

        synchronized (this) {
            if (securityFramework == null) {
                securityFramework = Native.load("Security", SecurityFramework.class);
            }

            return securityFramework;
        }
    }

    private CoreFoundation coreFoundation() {
        CoreFoundation currentFramework = coreFoundation;

        if (currentFramework != null) {
            return currentFramework;
        }

        synchronized (this) {
            if (coreFoundation == null) {
                coreFoundation = Native.load("CoreFoundation", CoreFoundation.class);
            }

            return coreFoundation;
        }
    }

    interface SecurityFramework extends Library {

        int SecKeychainFindGenericPassword(
                Pointer keychainOrArray,
                int serviceNameLength,
                byte[] serviceName,
                int accountNameLength,
                byte[] accountName,
                IntByReference passwordLength,
                PointerByReference passwordData,
                PointerByReference itemRef
        );

        int SecKeychainAddGenericPassword(
                Pointer keychain,
                int serviceNameLength,
                byte[] serviceName,
                int accountNameLength,
                byte[] accountName,
                int passwordLength,
                byte[] passwordData,
                PointerByReference itemRef
        );

        int SecKeychainItemModifyAttributesAndData(
                Pointer itemRef,
                Pointer attributes,
                int length,
                byte[] data
        );

        int SecKeychainItemDelete(Pointer itemRef);

        int SecKeychainItemFreeContent(Pointer attributes, Pointer data);
    }

    interface CoreFoundation extends Library {

        void CFRelease(Pointer value);
    }

    private final class KeychainLookup implements AutoCloseable {

        private final int status;
        private final int passwordLength;
        private Pointer passwordData;
        private Pointer itemRef;

        private KeychainLookup(int status, int passwordLength, Pointer passwordData, Pointer itemRef) {
            this.status = status;
            this.passwordLength = passwordLength;
            this.passwordData = passwordData;
            this.itemRef = itemRef;
        }

        private int status() {
            return status;
        }

        private Pointer itemRef() {
            if (itemRef == null) {
                throw new CredentialException("macOS Keychain item reference is missing.");
            }

            return itemRef;
        }

        private byte[] passwordBytes() {
            if (passwordLength <= 0 || passwordData == null) {
                return new byte[0];
            }

            return passwordData.getByteArray(0, passwordLength);
        }

        @Override
        public void close() {
            if (passwordData != null) {
                securityFramework().SecKeychainItemFreeContent(Pointer.NULL, passwordData);
                passwordData = null;
            }

            if (itemRef != null) {
                coreFoundation().CFRelease(itemRef);
                itemRef = null;
            }
        }
    }
}
