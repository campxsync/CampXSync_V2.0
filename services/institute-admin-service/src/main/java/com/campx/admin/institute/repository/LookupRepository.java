package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.LookupType;
import com.campx.admin.institute.model.InstituteModels.LookupValue;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing reference lookup domains and entries (Item 25:
 * {@code core.lookup_types} & {@code core.lookup_values}).
 */
public interface LookupRepository {

    LookupType createType(UserSecurityContext context, LookupType type);

    Optional<LookupType> findTypeById(UserSecurityContext context, String id);

    Optional<LookupType> findTypeByCode(UserSecurityContext context, String code);

    List<LookupType> listTypes(UserSecurityContext context);

    LookupType updateType(UserSecurityContext context, LookupType type);

    void deleteType(UserSecurityContext context, String id);

    LookupValue createValue(UserSecurityContext context, LookupValue value);

    Optional<LookupValue> findValueById(UserSecurityContext context, String id);

    Optional<LookupValue> findValueByCode(UserSecurityContext context, String lookupTypeId, String code);

    List<LookupValue> listValuesByType(UserSecurityContext context, String lookupTypeId);

    LookupValue updateValue(UserSecurityContext context, LookupValue value);

    void deleteValue(UserSecurityContext context, String id);
}
