package vorga.phazeclient.implement.menu.components.implement.other;

import vorga.phazeclient.base.util.Lang;

public final class ConfigShareApi {
    private static volatile String lastError = Lang.t("status.cloud_disabled_detail");

    private ConfigShareApi() {}

    public static String getLastError() {
        return lastError;
    }

    public static String upload(String share, String author, Integer maxUses, String name) {
        lastError = Lang.t("status.cloud_disabled_detail");
        return null;
    }

    public static String upload(String share, String author, Integer maxUses) {
        return upload(share, author, maxUses, null);
    }

    public static String upload(String share) {
        return upload(share, null, null, null);
    }

    public static DownloadResult downloadFull(String code) {
        lastError = Lang.t("status.cloud_disabled_detail");
        return null;
    }

    public static String download(String code) {
        DownloadResult result = downloadFull(code);
        return result == null ? null : result.payload;
    }

    public static final class DownloadResult {
        public final String payload;
        public final String name;

        public DownloadResult(String payload, String name) {
            this.payload = payload;
            this.name = name;
        }
    }
}
