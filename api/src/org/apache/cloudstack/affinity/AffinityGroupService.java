package org.apache.cloudstack.affinity;

import java.util.List;

public interface AffinityGroupService {

    /**
     * Creates an affinity/anti-affinity group for the given account/domain.
     *
     * @param account
     * @param domainId
     * @param name
     * @param type
     * @param description
     * @return AffinityGroup
     */

    AffinityGroup createAffinityGroup(String account, Long domainId, String affinityGroupName,
            String affinityGroupType, String description);

    /**
     * Creates an affinity/anti-affinity group.
     *
     * @param affinityGroupId
     * @param account
     * @param domainId
     * @param affinityGroupName
     * @param vmId
     */
    void deleteAffinityGroup(Long affinityGroupId, String account, Long domainId, String affinityGroupName, Long vmId);

    /**
     * Lists Affinity Groups
     *
     * @param account
     * @param domainId
     * @param affinityGroupId
     * @param affinityGroupName
     * @param affinityGroupType
     * @param vmId
     * @return
     */
    List<AffinityGroup> listAffinityGroups(String account, Long domainId, Long affinityGroupId,
            String affinityGroupName, String affinityGroupType, Long vmId);


    /**
     * List group types available in deployment
     *
     * @return
     */
    List<String> listAffinityGroupTypes();

}
