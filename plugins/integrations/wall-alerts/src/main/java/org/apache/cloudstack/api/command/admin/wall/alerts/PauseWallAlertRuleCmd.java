// src/org/apache/cloudstack/api/command/admin/wall/alerts/PauseWallAlertRuleCmd.java
package org.apache.cloudstack.api.command.admin.wall.alerts;

import com.cloud.user.Account;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.SuccessResponse;
import org.apache.cloudstack.wallAlerts.service.WallAlertsService;

import javax.inject.Inject;

@APICommand(name = PauseWallAlertRuleCmd.APINAME,
        description = "Pause/Resume evaluation of a Wall alert rule",
        responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false,
        responseView = ResponseObject.ResponseView.Full)
public class PauseWallAlertRuleCmd extends BaseCmd {

    public static final String APINAME = "pauseWallAlertRule";

    // ✅ Optional 로 수정
    @Parameter(name = ApiConstants.ID, type = CommandType.STRING, required = false,
            description = "ID in the form 'dashboardUid:panelId' (e.g., dec041amqw4cge:77)")
    private String id;

    @Parameter(name = "namespace", type = CommandType.STRING, required = false,
            description = "Folder / namespace hint (name or uid)")
    private String namespace;

    @Parameter(name = "group", type = CommandType.STRING, required = false,
            description = "Ruler group name")
    private String group;

    @Parameter(name = "ruleUid", type = CommandType.STRING, required = false,
            description = "Grafana alert rule UID (preferred)")
    private String ruleUid;

    @Parameter(name = "paused", type = CommandType.BOOLEAN, required = true,
            description = "true to pause, false to resume")
    private Boolean paused;

    public String getId() { return id; }
    public String getNamespace() { return namespace; }
    public String getGroup() { return group; }
    public String getRuleUid() { return ruleUid; }
    public Boolean getPaused() { return paused; }

    @Inject
    private WallAlertsService wallAlertsService;

    @Override
    public void execute() {
        if (paused == null) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "'paused' is required");
        }

        final boolean ok;
        if (id != null && !id.isBlank()) {
            ok = wallAlertsService.pauseWallAlertRuleById(id, paused);
        } else if (ruleUid != null && !ruleUid.isBlank()   // ruleUid만으로도 허용
                && (namespace == null || namespace.isBlank())
                && (group == null || group.isBlank())) {
            ok = wallAlertsService.pauseWallAlertRuleByUid(ruleUid, paused);
        } else {
            if (namespace == null || namespace.isBlank()
                    || group == null || group.isBlank()
                    || ruleUid == null || ruleUid.isBlank()) {
                throw new ServerApiException(ApiErrorCode.PARAM_ERROR,
                        "Provide 'id', or just 'ruleUid', or full 'namespace','group','ruleUid'.");
            }
            ok = wallAlertsService.pauseWallAlertRule(namespace, group, ruleUid, paused);
        }

        if (!ok) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to change pause state");
        }
        setResponseObject(new SuccessResponse(getCommandName()));
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
