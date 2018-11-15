package org.povworld.sbb;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.povworld.collection.Collection;
import org.povworld.collection.mutable.ArrayList;
import org.povworld.collection.mutable.HashMultiMap;
import org.povworld.sbb.Input.Scenario;
import org.povworld.sbb.Input.SectionRequirement;
import org.povworld.sbb.Input.ServiceIntention;

public class ConnectionRepository {

	public static class Connection {
		public final String intentionFrom;
		public final String intentionTo;
		public final String markerFrom;
		public final String markerTo;
		public final int minConnectionTime;

		public Connection(String intentionFrom, String intentionTo, String markerFrom, String markerTo,
				int minConnectionTime) {
			this.intentionFrom = intentionFrom;
			this.intentionTo = intentionTo;
			this.markerFrom = markerFrom;
			this.markerTo = markerTo;
			this.minConnectionTime = minConnectionTime;
		}
		
		@Override
		public String toString() {
			return "from " + intentionFrom + "/" + markerFrom + " to " + intentionTo + "/" + markerTo + " in "
					+ minConnectionTime + "s";
		}
	}

	private static final Logger logger = Logger.getLogger(ConnectionRepository.class.getSimpleName());

	private final ArrayList<Connection> connections = new ArrayList<>();
	private final HashMultiMap<String, Connection> connectionsIn = new HashMultiMap<>();
	private final HashMultiMap<String, Connection> connectionsOut = new HashMultiMap<>();

	private ConnectionRepository() {

	}

	public static ConnectionRepository create(Scenario scenario) {
		ConnectionRepository result = new ConnectionRepository();
		if (!Debug.ENDABLE_CONNECTIONS) {
			return result;
		}
		for (ServiceIntention intention : scenario.getServiceIntentionsList()) {
			for (SectionRequirement requirement : intention.getSectionRequirementsList()) {
				for (org.povworld.sbb.Input.Connection connection : requirement.getConnectionsList()) {
					result.add(intention.getId(), connection.getOntoServiceIntention(), requirement.getSectionMarker(),
							connection.getOntoSectionMarker(), connection.getMinConnectionTimeSeconds());
				}
			}
		}
		return result;
	}

	private void add(String intentionFrom, String intentionTo, String markerFrom, String markerTo,
			int minConnectionTime) {
		logger.log(Level.INFO, "Connection from {0}/{1} to {2}/{3} with at least {4}s.",
				new Object[] { intentionFrom, intentionTo, markerFrom, markerTo, minConnectionTime });
		Connection c = new Connection(intentionFrom, intentionTo, markerFrom, markerTo, minConnectionTime);
		connections.push(c);
		connectionsIn.put(intentionTo, c);
		connectionsOut.put(intentionFrom, c);
	}

	
	public Collection<Connection> getAll() {
		return connections;
	}

}
