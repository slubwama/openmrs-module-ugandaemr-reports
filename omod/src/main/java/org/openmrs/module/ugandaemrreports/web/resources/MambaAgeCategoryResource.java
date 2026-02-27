package org.openmrs.module.ugandaemrreports.web.resources;

import org.openmrs.api.context.Context;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.ugandaemrreports.model.MambaAgeCategory;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.*;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.List;

@Resource(
        name = RestConstants.VERSION_1 + "/mambaagecategory",
        supportedClass = MambaAgeCategory.class,
        supportedOpenmrsVersions = { "2.*", "3.*" }
)
public class MambaAgeCategoryResource extends DelegatingCrudResource<MambaAgeCategory> {

    private UgandaEMRReportsService service() {
        return Context.getService(UgandaEMRReportsService.class);
    }

    @Override
    public MambaAgeCategory getByUniqueId(String uuid) {
        return service().getAgeCategoryByUuid(uuid);
    }

    @Override
    protected void delete(MambaAgeCategory category, String reason, RequestContext context) throws ResponseException {
        if (reason == null || reason.trim().isEmpty()) reason = "Retired via REST";
        service().retireAgeCategory(category, reason);
    }

    @Override
    public void purge(MambaAgeCategory category, RequestContext context) throws ResponseException {
        service().purgeAgeCategory(category);
    }

    @Override
    public MambaAgeCategory newDelegate() {
        return new MambaAgeCategory();
    }

    @Override
    public MambaAgeCategory save(MambaAgeCategory category) {
        return service().saveAgeCategory(category);
    }

    @Override
    public PageableResult doGetAll(RequestContext context) throws ResponseException {

        String q = context.getParameter("q");
        boolean includeRetired = Boolean.parseBoolean(
                context.getParameter("includeRetired") != null ? context.getParameter("includeRetired") : "false"
        );
Context.getAdministrationService().executeSQL("",false);
        Boolean activeOnly = null;
        String activeOnlyStr = context.getParameter("activeOnly");
        if (activeOnlyStr != null && !activeOnlyStr.trim().isEmpty()) {
            activeOnly = Boolean.parseBoolean(activeOnlyStr);
        }

        List<MambaAgeCategory> list = service().getAgeCategories(
                q,
                includeRetired,
                activeOnly,
                context.getStartIndex(),
                context.getLimit()
        );

        return new NeedsPaging<>(list, context);
    }

    @Override
    protected PageableResult doSearch(RequestContext context) throws ResponseException {
        return doGetAll(context);
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {

        // ✅ REQUIRED so nested ageCategory can serialize as REF
        if (rep instanceof RefRepresentation) {
            DelegatingResourceDescription d = new DelegatingResourceDescription();
            d.addProperty("uuid");
            d.addProperty("display", findMethod("getDisplayString"));
            return d;
        }

        if (rep instanceof DefaultRepresentation) {
            DelegatingResourceDescription d = new DelegatingResourceDescription();
            d.addProperty("uuid");
            d.addProperty("display", findMethod("getDisplayString"));
            d.addProperty("name");
            d.addProperty("description");
            d.addProperty("code");
            d.addProperty("version");
            d.addProperty("effectiveFrom");
            d.addProperty("effectiveTo");
            d.addProperty("active");
            d.addProperty("retired");
            return d;
        }

        if (rep instanceof FullRepresentation) {
            DelegatingResourceDescription d = new DelegatingResourceDescription();
            d.addProperty("uuid");
            d.addProperty("display", findMethod("getDisplayString"));
            d.addProperty("name");
            d.addProperty("description");
            d.addProperty("code");
            d.addProperty("version");
            d.addProperty("effectiveFrom");
            d.addProperty("effectiveTo");
            d.addProperty("active");
            d.addProperty("ageGroups"); // careful: may be lazy
            d.addProperty("retired");
            d.addProperty("retireReason");
            d.addProperty("auditInfo", findMethod("getAuditInfo"));
            return d;
        }

        return null;
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() {
        DelegatingResourceDescription d = new DelegatingResourceDescription();
        d.addRequiredProperty("name");
        d.addRequiredProperty("code");
        d.addProperty("description");
        d.addProperty("version");
        d.addProperty("effectiveFrom");
        d.addProperty("effectiveTo");
        d.addProperty("active");
        return d;
    }

    @Override
    public DelegatingResourceDescription getUpdatableProperties() {
        return getCreatableProperties();
    }

    public String getDisplayString(MambaAgeCategory c) {
        return c.getName() != null ? c.getName() : c.getUuid();
    }
}