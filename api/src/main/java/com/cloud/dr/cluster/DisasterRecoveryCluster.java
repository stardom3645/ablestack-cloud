package com.cloud.dr.cluster;

import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

import java.util.Date;

public interface DisasterRecoveryCluster extends InternalIdentity, Identity {

    public enum DrClusterStatus {
        Disabled, Enabled, Error
    }

    public enum MirroringAgentStatus {
        Disabled, Enabled, Error
    }

    long getId();
    String getUuid();
    long getMsHostId();
    String getName();
    String getDescription();
    String getDrClusterUrl();
    String getDrClusterType();
    String getDrClusterStatus();
    String getMirroringAgentStatus();
    String getApiKey();
    String getSecretKey();
    Date getCreated();
    Date getRemoved();

}
