package org.openmrs.module.ugandaemrreports.web.resources;

import org.openmrs.api.context.Context;
import org.openmrs.module.ugandaemrreports.api.UgandaEMRReportsService;
import org.openmrs.module.ugandaemrreports.model.MambaAgeGroup;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.*;
import org.openmrs.module.webservices.rest.web.resource.impl.BaseDelegatingResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.List;

/**
 * Read-only list endpoint for age groups by category.
 *
 * GET /ws/rest/v1/reportbuilder/mambaagegroup?categoryUuid=...&activeOnly=true
 * GET /ws/rest/v1/reportbuilder/mambaagegroup?categoryCode=...&activeOnly=true
 *
 * (You can do full CRUD too, but list-by-category is usually what the UI needs.)
 */
@Resource(
        name = RestConstants.VERSION_1 + "/mambaagegroup",
        supportedClass = MambaAgeGroupListResource.AgeGroupList.class,
        supportedOpenmrsVersions = { "2.*", "3.*" }
)
public class MambaAgeGroupListResource extends BaseDelegatingResource<MambaAgeGroupListResource.AgeGroupList> {

    public static class AgeGroupList {
        private List<MambaAgeGroup> results;

        public AgeGroupList() {}
        public AgeGroupList(List<MambaAgeGroup> results) { this.results = results; }

        public List<MambaAgeGroup> getResults() { return results; }
        public void setResults(List<MambaAgeGroup> results) { this.results = results; }
    }

    private UgandaEMRReportsService service() {
        return Context.getService(UgandaEMRReportsService.class);
    }

    @Override
    public AgeGroupList newDelegate() {
        // default: empty; caller should use query params
        return new AgeGroupList();
    }

    @Override
    public AgeGroupList save(AgeGroupList delegate) {
        throw new UnsupportedOperationException("Read-only endpoint");
    }

    @Override
    public AgeGroupList getByUniqueId(String uniqueId) {
        return null;
    }

    @Override
    protected void delete(AgeGroupList ageGroupList, String s, RequestContext requestContext) throws ResponseException {

    }

    @Override
    public void purge(AgeGroupList ageGroupList, RequestContext requestContext) throws ResponseException {

    }

    /**
     * RESTWS doesn't provide a "doGetAll" hook for BaseDelegatingResource,
     * so we keep this as a resource that is representation-only and handled
     * by default with the "newDelegate" pattern is limited.
     *
     * RECOMMENDED: Make AgeGroup a proper CrudResource too (below).
     */
    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        DelegatingResourceDescription d = new DelegatingResourceDescription();
        d.addProperty("results");
        return d;
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() {
        return null;
    }
}