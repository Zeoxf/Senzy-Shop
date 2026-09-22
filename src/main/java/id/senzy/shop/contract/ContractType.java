package id.senzy.shop.contract;

import java.util.Locale;

public enum ContractType {
    DAILY, WEEKLY;

    public String metaResetKey() {
        return this == DAILY ? "next_daily_reset" : "next_weekly_reset";
    }

    public String metaGenerationKey() {
        return this == DAILY ? "daily_generation_id" : "weekly_generation_id";
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
