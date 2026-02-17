package org.openmrs.module.ugandaemrreports.web.resources;

import org.openmrs.api.context.Context;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.ugandaemrreports.model.MambaIndicator;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.representation.*;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.List;

@Resource(name = RestConstants.VERSION_1 + "/mambaindicator", supportedClass = MambaIndicator.class, supportedOpenmrsVersions = {"1.8 - 9.0.*"})
public class MambaIndicatorResource extends DelegatingCrudResource<MambaIndicator> {

    private UgandaEMRReportsService service() {
        return Context.getService(UgandaEMRReportsService.class);
    }

    @Override
    public MambaIndicator getByUniqueId(String uuid) {
        return service().getMambaIndicatorByUuid(uuid);
    }

    @Override
    public void purge(MambaIndicator indicator, RequestContext context) {
        service().purgeMambaIndicator(indicator);
    }

    @Override
    public MambaIndicator newDelegate() {
        return new MambaIndicator();
    }

    @Override
    public MambaIndicator save(MambaIndicator indicator) {
        return service().saveMambaIndicator(indicator);
    }

    @Override
    public PageableResult doGetAll(RequestContext context) {
        String q = context.getParameter("q");
        String kindStr = context.getParameter("kind");
        boolean includeRetired = Boolean.parseBoolean(
                context.getParameter("includeRetired") != null ? context.getParameter("includeRetired") : "false"
        );

        MambaIndicator.Kind kind = null;
        if (kindStr != null && !kindStr.trim().isEmpty()) {
            kind = MambaIndicator.Kind.valueOf(kindStr);
        }

        Integer startIndex = context.getStartIndex();
        Integer limit = context.getLimit();

        List<MambaIndicator> results = service().getMambaIndicators(q, kind, includeRetired, startIndex, limit);
        return new NeedsPaging<>(results, context);
    }

    @Override
    protected PageableResult doSearch(RequestContext context) {
        // treat search same as getAll but require q
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
            d.addProperty("kind");
            d.addProperty("defaultValueType");
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
            d.addProperty("kind");
            d.addProperty("defaultValueType");
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
        d.addRequiredProperty("kind");
        d.addProperty("defaultValueType");
        d.addRequiredProperty("configJson");
        d.addProperty("metaJson");
        return d;
    }

    @Override
    public DelegatingResourceDescription getUpdatableProperties() {
        return getCreatableProperties();
    }

    public String getDisplayString(MambaIndicator indicator) {
        // what "display" returns
        if (indicator.getName() != null && indicator.getCode() != null) {
            return indicator.getName() + " (" + indicator.getCode() + ")";
        }
        return indicator.getName() != null ? indicator.getName() : indicator.getUuid();
    }

    @Override
    protected void delete(MambaIndicator indicator, String reason, RequestContext context) throws ResponseException {
        // RESTWS "DELETE" defaults to retire unless purge is requested
        if (reason == null || reason.trim().isEmpty()) {
            reason = "Retired via REST";
        }
        service().retireMambaIndicator(indicator, reason);
    }


}