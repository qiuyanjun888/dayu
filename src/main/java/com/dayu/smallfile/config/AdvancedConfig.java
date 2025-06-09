package com.dayu.smallfile.config;

import lombok.Data;

@Data
public class AdvancedConfig {

    private boolean metadataCache;
    private boolean dryRun;
    private boolean cleanup;
    private String backupDir;
}
