package org.povworld.sbb;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map.Entry;

import org.povworld.sbb.Input.Scenario.Builder;
import org.povworld.sbb.Input.Connection;
import org.povworld.sbb.Input.Resource;
import org.povworld.sbb.Input.Route;
import org.povworld.sbb.Input.RoutePath;
import org.povworld.sbb.Input.RouteSection;
import org.povworld.sbb.Input.SectionRequirement;
import org.povworld.sbb.Input.ServiceIntention;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;

public class InputParser {

	public static Input.Scenario parseScenario(File file) throws IOException {
		JsonElement json = new JsonParser().parse(new FileReader(file));
		cleanNullElements(json);
		Builder builder = Input.Scenario.newBuilder();
		JsonFormat.parser().ignoringUnknownFields().merge(json.toString(), builder);
		parseTimeStrings(builder);
		return builder.build();
	}

	private static void cleanNullElements(JsonElement jsonElement) {
		if (jsonElement.isJsonObject()) {
			JsonObject obj = jsonElement.getAsJsonObject();
			for (Entry<String, JsonElement> e : obj.entrySet()) {
				cleanNullElements(e.getValue());
			}
		} else if (jsonElement.isJsonArray()) {
			JsonArray array = jsonElement.getAsJsonArray();
			if (array.size() == 1 && array.get(0).isJsonNull()) {
				array.remove(0);
			} else {
				for (JsonElement e : array) {
					cleanNullElements(e);
				}
			}
		}
	}

	private static void parseTimeStrings(Builder builder) {
		for (ServiceIntention.Builder si : builder.getServiceIntentionsBuilderList()) {
			for (SectionRequirement.Builder sr : si.getSectionRequirementsBuilderList()) {
				sr.setMinStoppingTimeSeconds(TimeUtil.parseDuration(sr.getMinStoppingTime()));
				sr.clearMinStoppingTime();
				for(Connection.Builder c: sr.getConnectionsBuilderList()) {
					c.setMinConnectionTimeSeconds(TimeUtil.parseDuration(c.getMinConnectionTime()));
					c.clearMinConnectionTime();
				}
			}
		}
		for (Route.Builder r : builder.getRoutesBuilderList()) {
			for (RoutePath.Builder rp : r.getRoutePathsBuilderList()) {
				for (RouteSection.Builder rs : rp.getRouteSectionsBuilderList()) {
					rs.setMinimumRunningTimeSeconds(TimeUtil.parseDuration(rs.getMinimumRunningTime()));
					rs.clearMinimumRunningTime();
				}
			}
		}
		for (Resource.Builder r : builder.getResourcesBuilderList()) {
			r.setReleaseTimeSeconds(TimeUtil.parseDuration(r.getReleaseTime()));
			r.clearReleaseTime();
		}
	}
}
