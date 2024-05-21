package com.cloud.dr.cluster;

import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

import java.util.Date;

public interface DisasterRecoveryCluster extends InternalIdentity, Identity {

    public enum DrClusterStatus {
        Disabled, Enabled, Created, Error
    }

    public enum MirroringAgentStatus {
        Disabled, Enabled, Created, Error
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
    String getDrClusterApiKey();
    String getDrClusterSecretKey();
    Date getCreated();
    Date getRemoved();

}