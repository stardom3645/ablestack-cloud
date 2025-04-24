package org.apache.cloudstack.api.response;

import com.cloud.host.Host;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

@EntityReference(value = {Host.class})
public class UpdateHostDevicesResponse extends BaseResponse {
    
    @SerializedName("devicename")
    @Param(description = "Device name")
    private String deviceName;

    @SerializedName("vmid")
    @Param(description = "ID of the VM the device is allocated to")
    private String vmId;

    @SerializedName("allocated")
    @Param(description = "Whether the device is allocated")
    private boolean allocated;

    public UpdateHostDevicesResponse() {
        super();
        setObjectName("updatehostdevices");
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getVmId() {
        return vmId;
    }

    public void setVmId(String vmId) {
        this.vmId = vmId;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public void setAllocated(boolean allocated) {
        this.allocated = allocated;
    }
} 