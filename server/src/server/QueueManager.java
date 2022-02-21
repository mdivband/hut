package server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import server.model.agents.Agent;
import server.model.Coordinate;
import server.model.task.Task;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class QueueManager {

	// Uniform Resource Identifier - The address at which the cloud service can be accessed
	private final String SERVERURI = "amqp://oijjhrkf:NtrL2nSC2I0Darx3q2S_SA7D0Eig-loz@lion.rmq.cloudamqp.com/oijjhrkf";

	private ConnectionFactory connectionFactory;
	private final Simulator simulator;

	private Map<String, DeclareOk> queues;

	private Logger LOGGER = Logger.getLogger(QueueManager.class.getName());

	public QueueManager(Simulator simulator) {
		this.simulator = simulator;
		queues = new HashMap<>();
		LOGGER.info(String.format("%s; QMST; QueueManager Fully Started; %s", Simulator.instance.getState().getTime(), this.initConnectionFactory()));
	}

	public String getCloudURI() {
		return SERVERURI;
	}

	private Channel getNewChannel(){
		try {
			return connectionFactory.newConnection().createChannel();
		} catch (IOException | TimeoutException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private boolean initConnectionFactory() {
		System.out.println("Connecting to RMQ: "+SERVERURI);
		connectionFactory = new ConnectionFactory();
		try {
			connectionFactory.setUri(SERVERURI);
		} catch (KeyManagementException | NoSuchAlgorithmException | URISyntaxException e) {
			e.printStackTrace();
		}
		connectionFactory.setRequestedHeartbeat(30);
		connectionFactory.setConnectionTimeout(30000);
		return addQueue("Meta_Drone_Data");
	}

	public boolean addQueue(String queueName) {
		try {
			Connection connection = connectionFactory.newConnection();
			Channel channel = connection.createChannel();
			queues.put(queueName, channel.queueDeclare(queueName, false, false, false, null));
			channel.close();
			connection.close();
		} catch (IOException | TimeoutException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public MessagePublisher createMessagePublisher() {
	    return new MessagePublisher();
    }

	private boolean publishMessage(String queueName, String message) {
		try {
			LOGGER.info(queueName +": " + message);
			Connection connection = connectionFactory.newConnection();
			Channel channel = connection.createChannel();
			channel.basicPublish("", queueName, null, message.getBytes());
			channel.close();
			connection.close();
		} catch (IOException | TimeoutException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	boolean initDroneDataConsumer() {
	    Consumer consumer = createDroneDataConsumer(getNewChannel());
		Connection connection;
		try {
			connection = connectionFactory.newConnection();
			Channel channel = connection.createChannel();
			channel.queueDeclare("Meta_Drone_Data", false, false, false, null);
			channel.basicConsume("Meta_Drone_Data", true, consumer);
		} catch (IOException | TimeoutException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}
	
	private void sendMarkerFinished(int markerID, String droneID){
		JsonObject data = new JsonObject();
		
		data.addProperty("Content", "MarkerFinished");
		data.addProperty("MarkerID", markerID);
		
		for (Map.Entry<String, DeclareOk> entry: queues.entrySet()){
			if(!entry.getKey().equals("UAV_TaskQueue_"+droneID) && !entry.getKey().equals("Meta_Drone_Data")){
				this.publishMessage(entry.getKey(), data.toString());
			}
		}
	}

	public class MessagePublisher {
		public boolean publishMessage(String queueName, String message){
			QueueManager.this.publishMessage(queueName, message);
			return true;
		}
	}

	private Consumer createDroneDataConsumer(Channel channel) {
		Consumer consumer;
		consumer = new DefaultConsumer(channel) {
			JsonObject metaData;
			JsonParser jsonParser = new JsonParser();
			Agent agent;
			Coordinate newCoordinate;
			String agentID;
			String content;
			String taskID;

			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
			    try {
                    metaData = jsonParser.parse(new String(body, "UTF-8")).getAsJsonObject();
                    agentID = metaData.get("ID").getAsString();
                    agent = simulator.getState().getAgent(agentID);
                    content = metaData.get("Content").getAsString();
                    if (agent != null) {
                        switch (content) {
                            case "Coordinates":
                                JsonObject coordinates = metaData.get("Coordinates").getAsJsonObject();
                                newCoordinate = new Coordinate(coordinates.get("Latitude").getAsDouble(), coordinates.get("Longitude").getAsDouble());
                                agent.setCoordinate(newCoordinate);
                                agent.setHeading(metaData.get("Heading").getAsInt());
                                agent.heartbeat();
                                break;
                            case "ManualControl":
                                boolean manualControl;
                                manualControl = metaData.get("ManualControl").getAsBoolean();
                                if (manualControl != agent.isManuallyControlled()) {
                                    agent.toggleManualControl();
                                    if (manualControl) {
                                        taskID = (metaData.get("MissionID") != null) ? metaData.get("MissionID").getAsString() : null;
                                        if (taskID != null) {
                                            Task task = simulator.getState().getTask(taskID);
                                            simulator.getTaskController().deleteTask(taskID, false);
                                            simulator.getTaskController().updateTaskPosition(taskID, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude());
                                            LOGGER.info("Manual control: " + agentID);
                                        }
                                    }
                                }
                                break;
                            case "MarkFinished":
                                LOGGER.info("Layout: " + ((metaData.get("FirstSetup").getAsBoolean()) ? 1 : 2) + " mark finished: " + metaData.get(content).getAsInt() + " Drone: " + agentID);
                                sendMarkerFinished(metaData.get(content).getAsInt(), agentID);
                                break;
                            case "POISpotted":
                                LOGGER.info("Layout: " + ((metaData.get("FirstSetup").getAsBoolean()) ? 1 : 2) + " POI spotted: " + metaData.get(content).getAsInt() + " Drone: " + agentID);
                                break;
                            case "Log":
                                LOGGER.info("MSGQ LOG: " + content);
                                break;
                            default:
                                break;
                        }
                    }
                }
                catch(Exception e) {
			        LOGGER.severe("Unable to handle drone meta data delivery: " + e.getMessage());
                }
			}
		};
		return consumer;
	}
	
}