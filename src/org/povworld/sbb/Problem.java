package org.povworld.sbb;

import javax.annotation.Nullable;

import org.povworld.collection.common.ObjectUtil;
import org.povworld.collection.mutable.HashMap;
import org.povworld.collection.mutable.IdentityHashMap;
import org.povworld.sbb.Input.Resource;
import org.povworld.sbb.Input.Route;
import org.povworld.sbb.Input.RoutePath;
import org.povworld.sbb.Input.RouteSection;
import org.povworld.sbb.Input.Scenario;
import org.povworld.sbb.Input.ServiceIntention;

public class Problem {
	private final Scenario scenario;
	
	// Map: intention id -> ServiceIntention
	private final HashMap<String, ServiceIntention> serviceIntentions = new HashMap<>();
	
	// Map: RouteSection -> RoutePath id
	private final HashMap<RouteSection, String> routeSectionPathId = new IdentityHashMap<>();
	
	// Map: route id -> RouteGraph
	private final HashMap<String, RouteGraph> routeGraphs = new HashMap<>();
	
	// Map: resource id -> Resource
	private final HashMap<String, Resource> resources = new HashMap<>();
	
	// Map: intention id -> GraphResourceOccupations
	private final HashMap<String, GraphResourceOccupations> resourceOccupations = new HashMap<>();
	
	public Problem(Scenario input) {
		this.scenario = input;
		
		indexRoutes();
		indexServiceIntentions();
		indexResources();
		indexResourceOccupations();
	}

	private void indexResourceOccupations() {
		for(ServiceIntention si: scenario.getServiceIntentionsList()) {
			RouteGraph graph = routeGraphs.get(si.getRoute());
			GraphResourceOccupations occupations = GraphResourceOccupations.create(graph, si);
			resourceOccupations.put(si.getId(), occupations);
		}
	}

	private void indexServiceIntentions() {
		for(ServiceIntention si: scenario.getServiceIntentionsList()) {
			serviceIntentions.put(si.getId(), si);
		}
	}

	private void indexRoutes() {
		for (Route route : scenario.getRoutesList()) {
			routeGraphs.put(route.getId(), RouteGraph.build(route));
			for(RoutePath routePath: route.getRoutePathsList()) {
				for(RouteSection section: routePath.getRouteSectionsList()) {
					routeSectionPathId.put(section, routePath.getId());
				}
			}
		}
	}
	
	private void indexResources() {
		for(Resource r: scenario.getResourcesList()) {
			resources.put(r.getId(), r);
		}
	}
	
	public Resource getResource(String resourceId) {
		return resources.get(resourceId);
	}

	public Iterable<Resource> getResources() {
		return scenario.getResourcesList();
	}

	public Scenario getScenario() {
		return scenario;
	}

	public RouteGraph getRouteGraph(String routeId) {
		return ObjectUtil.checkNotNull(routeGraphs.get(routeId));
	}
	
	public ServiceIntention getServiceIntention(String serviceIntentionId) {
		return serviceIntentions.get(serviceIntentionId);
	}
	
	public String getRoutePathId(RouteSection section) {
		return routeSectionPathId.get(section);
	}
	
	public GraphResourceOccupations getResourceOccupations(String intentionId) {
		return resourceOccupations.get(intentionId);
	}
	
	@Nullable
	public String getSectionMarker(RouteSection section) {
		if (section.getSectionMarkerCount() == 0) {
			return null;
		}
		return section.getSectionMarker(0);
	}
	
}
