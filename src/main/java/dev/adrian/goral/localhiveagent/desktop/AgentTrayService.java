package dev.adrian.goral.localhiveagent.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentTrayService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentTrayService.class);
    private static final String TRAY_ICON_RESOURCE =
            "/dev/adrian/goral/localhiveagent/desktop/localhive-tray.png";

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean hideNotificationShown = new AtomicBoolean(false);

    private volatile SystemTray systemTray;
    private volatile TrayIcon trayIcon;
    private MenuItem modeItem;
    private MenuItem heartbeatItem;
    private MenuItem toggleWorkerModeItem;

    public boolean start(AgentTrayActions actions, AgentTrayState initialState) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(initialState);

        if (initialized.get()) {
            updateState(initialState);
            return true;
        }

        boolean traySupported;

        try {
            traySupported = SystemTray.isSupported();
        } catch (RuntimeException exception) {
            log.warn("System tray support could not be checked. Dashboard close will use the standard application lifecycle.", exception);
            return false;
        }

        if (!traySupported) {
            log.warn("System tray is not supported. Dashboard close will use the standard application lifecycle.");
            return false;
        }

        Image trayImage = loadTrayImage();

        try {
            runOnEventQueueAndWait(() -> {
                PopupMenu menu = createPopupMenu(actions);
                TrayIcon icon = new TrayIcon(trayImage, "LocalHive Agent", menu);
                icon.setImageAutoSize(true);
                icon.addActionListener(event -> actions.openDashboard());

                SystemTray tray = SystemTray.getSystemTray();
                tray.add(icon);

                this.systemTray = tray;
                this.trayIcon = icon;
                this.initialized.set(true);

                applyState(initialState);
            });

            log.info("System tray initialized.");
            return true;
        } catch (Exception exception) {
            log.warn("System tray could not be initialized. Dashboard close will exit the application.", exception);
            close();
            return false;
        }
    }

    public void updateState(AgentTrayState state) {
        Objects.requireNonNull(state);

        if (!initialized.get()) {
            return;
        }

        EventQueue.invokeLater(() -> applyState(state));
    }

    public void showDashboardHiddenNotificationOnce() {
        if (!initialized.get() || !hideNotificationShown.compareAndSet(false, true)) {
            return;
        }

        EventQueue.invokeLater(() -> {
            TrayIcon currentIcon = trayIcon;

            if (!initialized.get() || currentIcon == null) {
                return;
            }

            try {
                currentIcon.displayMessage(
                        "LocalHive Agent",
                        "LocalHive Agent is still running in the system tray.",
                        TrayIcon.MessageType.INFO
                );
            } catch (RuntimeException exception) {
                log.debug("System tray notification could not be displayed.", exception);
            }
        });
    }

    @Override
    public void close() {
        if (!initialized.compareAndSet(true, false)) {
            return;
        }

        try {
            runOnEventQueueAndWait(() -> {
                TrayIcon currentIcon = trayIcon;
                SystemTray currentTray = systemTray;

                if (currentIcon != null && currentTray != null) {
                    currentTray.remove(currentIcon);
                }

                trayIcon = null;
                systemTray = null;
            });
        } catch (Exception exception) {
            log.warn("System tray icon could not be removed cleanly.", exception);
        }
    }

    private PopupMenu createPopupMenu(AgentTrayActions actions) {
        PopupMenu menu = new PopupMenu();

        MenuItem titleItem = new MenuItem("LocalHive Agent");
        titleItem.setEnabled(false);

        modeItem = new MenuItem();
        modeItem.setEnabled(false);

        heartbeatItem = new MenuItem();
        heartbeatItem.setEnabled(false);

        MenuItem openDashboardItem = new MenuItem("Open Dashboard");
        openDashboardItem.addActionListener(event -> actions.openDashboard());

        toggleWorkerModeItem = new MenuItem();
        toggleWorkerModeItem.addActionListener(event -> actions.toggleWorkerMode());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(event -> actions.exitApplication());

        menu.add(titleItem);
        menu.addSeparator();
        menu.add(modeItem);
        menu.add(heartbeatItem);
        menu.addSeparator();
        menu.add(openDashboardItem);
        menu.add(toggleWorkerModeItem);
        menu.addSeparator();
        menu.add(exitItem);

        return menu;
    }

    private void applyState(AgentTrayState state) {
        if (!initialized.get() || modeItem == null || heartbeatItem == null || toggleWorkerModeItem == null) {
            return;
        }

        modeItem.setLabel(state.modeLabel());
        heartbeatItem.setLabel(state.heartbeatLabel());
        toggleWorkerModeItem.setLabel(state.workerModeActionLabel());
        toggleWorkerModeItem.setEnabled(state.workerApiReady());
    }

    private Image loadTrayImage() {
        try (InputStream inputStream = AgentTrayService.class.getResourceAsStream(TRAY_ICON_RESOURCE)) {
            if (inputStream == null) {
                log.warn("Tray icon resource was not found: {}", TRAY_ICON_RESOURCE);
                return createFallbackTrayImage();
            }

            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) {
                log.warn("Tray icon resource could not be decoded: {}", TRAY_ICON_RESOURCE);
                return createFallbackTrayImage();
            }

            return image;
        } catch (IOException exception) {
            log.warn("Tray icon resource could not be loaded: {}", TRAY_ICON_RESOURCE, exception);
            return createFallbackTrayImage();
        }
    }

    private static Image createFallbackTrayImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(24, 24, 27));
            graphics.fillRoundRect(1, 1, 30, 30, 8, 8);
            graphics.setColor(new Color(245, 197, 66));
            graphics.setStroke(new BasicStroke(2.4f));
            graphics.drawRoundRect(4, 4, 24, 24, 6, 6);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

            String text = "LH";
            FontMetrics metrics = graphics.getFontMetrics();
            int x = (image.getWidth() - metrics.stringWidth(text)) / 2;
            int y = (image.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            graphics.drawString(text, x, y);
        } finally {
            graphics.dispose();
        }

        return image;
    }

    private static void runOnEventQueueAndWait(AwtOperation operation) throws Exception {
        if (EventQueue.isDispatchThread()) {
            operation.run();
            return;
        }

        AtomicReference<Exception> failure = new AtomicReference<>();

        try {
            EventQueue.invokeAndWait(() -> {
                try {
                    operation.run();
                } catch (Exception exception) {
                    failure.set(exception);
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }

        if (failure.get() != null) {
            throw failure.get();
        }
    }

    @FunctionalInterface
    private interface AwtOperation {

        void run() throws Exception;
    }
}
