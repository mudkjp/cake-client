package dev.mudkip.cake.discord;

import dev.mudkip.cake.CakeClient;
import fun.crashsystem.jdrpc.DiscordIPC;
import fun.crashsystem.jdrpc.DiscordIPCConfig;
import fun.crashsystem.jdrpc.activity.Activity;
import org.jspecify.annotations.Nullable;

public final class DiscordRpc {
    private static final long RETRY_DELAY = 5000L;
    private static final long IDLE_DELAY = 500L;
    private static final long COMMAND_TIMEOUT = 5000L;

    private final long applicationId;
    private @Nullable Worker worker;

    public DiscordRpc(long applicationId) {
        this.applicationId = applicationId;
    }

    public void start() {
        if (this.worker != null) {
            return;
        }

        this.worker = new Worker();
        this.worker.start();
    }

    public void update(Activity activity) {
        var worker = this.worker;

        if (worker != null) {
            worker.update(activity);
        }
    }

    public void stop() {
        var worker = this.worker;

        if (worker != null) {
            this.worker = null;
            worker.requestStop();
        }
    }

    private final class Worker extends Thread {
        private volatile @Nullable Activity pending;
        private volatile @Nullable Activity sent;
        private volatile boolean connected;
        private volatile boolean running;

        private Worker() {
            super("Cake Discord RPC");
            this.setDaemon(true);
            this.running = true;
        }

        @Override
        public void run() {
            var ipc = DiscordIPC.create(DiscordIPCConfig.builder()
                    .clientId(DiscordRpc.this.applicationId)
                    .reconnect(true)
                    .commandTimeoutMs(DiscordRpc.COMMAND_TIMEOUT)
                    .build());

            while (this.running && !this.connect(ipc)) {
                this.pause(DiscordRpc.RETRY_DELAY);
            }

            if (this.running) {
                while (this.running) {
                    this.flush(ipc);
                    this.pause(DiscordRpc.IDLE_DELAY);
                }

                try {
                    ipc.clearActivity();
                } catch (Exception exception) {
                    CakeClient.LOGGER.debug("Failed to clear Discord Rich Presence", exception);
                }
            }

            ipc.close();
        }

        private boolean connect(DiscordIPC ipc) {
            try {
                ipc.connect();
                return true;
            } catch (Exception exception) {
                CakeClient.LOGGER.debug("Failed to connect to Discord", exception);
                return false;
            }
        }

        private void update(Activity activity) {
            this.pending = activity;
        }

        private void flush(DiscordIPC ipc) {
            if (!ipc.isConnected()) {
                this.connected = false;
                return;
            }

            if (!this.connected) {
                this.connected = true;
                this.sent = null;
            }

            var activity = this.pending;

            if (activity == null || activity.equals(this.sent)) {
                return;
            }

            try {
                ipc.setActivity(activity);
                this.sent = activity;
            } catch (Exception exception) {
                CakeClient.LOGGER.debug("Failed to update Discord Rich Presence", exception);
                this.sent = null;
            }
        }

        private void requestStop() {
            this.running = false;
            this.interrupt();
        }

        private void pause(long milliseconds) {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException exception) {
                Thread.interrupted();
            }
        }
    }
}
