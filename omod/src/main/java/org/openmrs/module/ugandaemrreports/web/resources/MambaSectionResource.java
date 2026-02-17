package org.openmrs.module.ugandaemrreports.web.resources;

import org.openmrs.api.context.Context;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.ugandaemrreports.model.MambaSection;
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

@Resource(name = RestConstants.VERSION_1 + "/mambasection", supportedClass = MambaSection.class, supportedOpenmrsVersions = {"1.8 - 9.0.*"})
public class MambaSectionResource extends DelegatingCrudResource<MambaSection> {

    private UgandaEMRReportsService service() {
        return Context.getService(UgandaEMRReportsService.class);
    }

    @Override
    public MambaSection getByUniqueId(String uuid) {
        return service().getMambaSectionByUuid(uuid);
    }

    @Override
    protected void delete(MambaSection section, String reason, RequestContext context) throws ResponseException {
        if (reason == null || reason.trim().isEmpty()) {
            reason = "Retired via REST";
        }
        service().retireMambaSection(section, reason);
    }

    @Override
    public void purge(MambaSection section, RequestContext context) throws ResponseException {
        service().purgeMambaSection(section);
    }

    @Override
    public MambaSection newDelegate() {
        return new MambaSection();
    }

    @Override
    public MambaSection save(MambaSection section) {
        return service().saveMambaSection(section);
    }

    @Override
    public PageableResult doGetAll(RequestContext context) throws ResponseException {

        String q = context.getParameter("q");
        boolean includeRetired = Boolean.parseBoolean(
                context.getParameter("includeRetired") != null ? context.getParameter("includeRetired") : "false"
        );

        List<MambaSection> results = service().getMambaSections(
                q,
                includeRetired,
                context.getStartIndex(),
                context.getLimit()
        );

        return new NeedsPaging<>(results, context);
    }

    @Override
    protected PageableResult doSearch(RequestContext context) throws ResponseException {
        return doGetAll(context);
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {

        if (rep instanceof DefaultRepresentation) {
            DelegatingResourceDescription d = new DelegatingResourceDescription();
            d.addProperty("uuid");
            d.addProperty("display");
            d.addProperty("name");
            d.addProperty("description");
            d.addProperty("code");
            d.addProperty("retired");
            d.addProperty("auditInfo", findMethod("getAuditInfo"));
            return d;
        }

        if (rep instanceof FullRepresentation) {
            DelegatingResourceDescription d = new DelegatingResourceDescription();
            d.addProperty("uuid");
            d.addProperty("display");
            d.addProperty("name");
            d.addProperty("description");
            d.addProperty("code");
            d.addProperty("configJson");
            d.addProperty("metaJson");
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
        d.addProperty("description");
        d.addProperty("code");
        d.addProperty("configJson");
        d.addProperty("metaJson");
        return d;
    }

    @Override
    public DelegatingResourceDescription getUpdatableProperties() {
        return getCreatableProperties();
    }

    public String getDisplayString(MambaSection section) {
        if (section.getName() != null && section.getCode() != null) {
            return section.getName() + " (" + section.getCode() + ")";
        }
        return section.getName() != null ? section.getName() : section.getUuid();
    }
}