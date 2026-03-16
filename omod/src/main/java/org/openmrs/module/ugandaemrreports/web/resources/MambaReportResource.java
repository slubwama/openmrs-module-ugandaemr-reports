package org.openmrs.module.ugandaemrreports.web.resources;

import org.openmrs.api.context.Context;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.ugandaemrreports.model.MambaReport;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.List;

@Resource(name = RestConstants.VERSION_1 + "/mambareport", supportedClass = MambaReport.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class MambaReportResource extends DelegatingCrudResource<MambaReport> {

    private UgandaEMRReportsService service() {
        return Context.getService(UgandaEMRReportsService.class);
    }

    @Override
    public MambaReport getByUniqueId(String uuid) {
        return service().getMambaReportByUuid(uuid);
    }

    @Override
    protected void delete(MambaReport report, String reason, RequestContext context) throws ResponseException {
        if (reason == null || reason.trim().isEmpty()) {
            reason = "Retired via REST";
        }
        service().retireMambaReport(report, reason);
    }

    @Override
    public void purge(MambaReport report, RequestContext context) throws ResponseException {
        service().purgeMambaReport(report);
    }

    @Override
    public MambaReport newDelegate() {
        return new MambaReport();
    }

    @Override
    public MambaReport save(MambaReport report) {
        return service().saveMambaReport(report);
    }

    @Override
    public PageableResult doGetAll(RequestContext context) throws ResponseException {
        String q = context.getParameter("q");
        boolean includeRetired = Boolean.parseBoolean(
                context.getParameter("includeRetired") != null ? context.getParameter("includeRetired") : "false"
        );

        List<MambaReport> results = service().getMambaReports(
                q,
                includeRetired,
                context.getStartIndex(),
                context.getLimit()
        );

        return new NeedsPaging<MambaReport>(results, context);
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
            d.addProperty("name");
            d.addProperty("description");
            d.addProperty("code");
            d.addProperty("retired");
            return d;
        }

        if (rep instanceof FullRepresentation) {
            DelegatingResourceDescription d = new DelegatingResourceDescription();
            d.addProperty("uuid");
            d.addProperty("name");
            d.addProperty("description");
            d.addProperty("code");
            d.addProperty("configJson");
            d.addProperty("metaJson");
            d.addProperty("retired");
            d.addProperty("retireReason");
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

    public String getDisplayString(MambaReport report) {
        if (report.getName() != null && report.getCode() != null) {
            return report.getName() + " (" + report.getCode() + ")";
        }
        return report.getName() != null ? report.getName() : report.getUuid();
    }
}