package org.ihtsdo.rvf.autoscaling;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AutoScalingManager {

	private Logger logger = LoggerFactory.getLogger(AutoScalingManager.class);
	
	@Autowired
	private InstanceManager instanceManager;
	
	@Autowired
	private ActiveMQConnectionFactory connectionFactory;
	
	private boolean isAutoScallingEnabled;
	
	private String queueName;
	
	private static int lastPolledQueueSize;

	private int maxRunningInstance;


	private ExecutorService executorService;
	
	private PooledConnectionFactory pooledConnectionFactory;

	private boolean shutDown;

	public AutoScalingManager(Boolean isAutoScalling, String destinationQueueName, Integer maxRunningInstance) {
		isAutoScallingEnabled = isAutoScalling.booleanValue();
		queueName = destinationQueueName;
		this.maxRunningInstance = maxRunningInstance;

	}

	@PostConstruct
	public void init() {
		logger.info("isAutoScalingEnabled:" + isAutoScallingEnabled);
		if (isAutoScallingEnabled) {
			pooledConnectionFactory = new PooledConnectionFactory(connectionFactory);
			pooledConnectionFactory.setMaxConnections(2);
			pooledConnectionFactory.setMaximumActiveSessionPerConnection(1);
			executorService = Executors.newSingleThreadExecutor();
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					manageInstances();
				}
			});
			executorService.shutdown();
		}
	}
	
	private void manageInstances() {
		boolean isFirstTime = true;
		List<String> activeInstances = null;
		LocalTime lastCheckTime = LocalTime.now();
		while (!shutDown) {
			try {
				if (isFirstTime) {
					isFirstTime = false;
					// check any running instances
					activeInstances = instanceManager.getActiveInstances();
					lastCheckTime = LocalTime.now();
				} else {
					int current = getQueueSize();
					if (current != lastPolledQueueSize || hourElapsedSinceLastCheck(lastCheckTime)) {
						logger.info("Total messages in queue:" + current);
						activeInstances = instanceManager.getActiveInstances();
						lastCheckTime = LocalTime.now();
					}
					if ((current > lastPolledQueueSize) || (current > activeInstances.size())) {
						if (current > lastPolledQueueSize) {
							logger.info("Messages have been increased by:" + (current - lastPolledQueueSize) + " since last poll.");
						}
						int totalToCreate = getTotalInstancesToCreate(current, activeInstances.size(), maxRunningInstance);
						if (totalToCreate != 0) {
							logger.info("Start creating " + totalToCreate + " new worker instance");
							long start = System.currentTimeMillis();
							List<String> newlyCreated = instanceManager.createInstance(totalToCreate);
							activeInstances.addAll(newlyCreated);
							logger.info("Time taken to create new intance in seconds:" + (System.currentTimeMillis() - start)/1000);
							Map<String,String> instanceIpAddressMap = instanceManager.getPublicIpAddress(newlyCreated);
							for (String instance : instanceIpAddressMap.keySet()) {
								logger.info("Instance {} created with public IP address {}", instance, instanceIpAddressMap.get(instance));
							}
						}
					}
					lastPolledQueueSize = current;
					try {
						Thread.sleep(30 * 1000);
					} catch (InterruptedException e) {
						logger.error("AutoScalingManager delay is interrupted.", e);
					}
				}
			} catch (Throwable t) {
				logger.error("Error occurred", t);
			}
		}
	}
	
	private boolean hourElapsedSinceLastCheck(LocalTime lastCheckTime) {
		return Duration.between(lastCheckTime, LocalTime.now()).abs().toHours() >= 1;
	}

	private int getTotalInstancesToCreate(int currentMsgSize, int currentActiveInstances, int maxRunningInstance) {
		int result = 0;
		if (currentActiveInstances < maxRunningInstance) {
			if (currentMsgSize <= currentActiveInstances) {
				logger.info("No new instance will be created as message size is:" + currentMsgSize);
			} else {
				if (currentMsgSize <= maxRunningInstance) {
					result = currentMsgSize - currentActiveInstances;
				} else {
					result = maxRunningInstance - currentActiveInstances;
				}
			}
		} else {
			logger.info("No new instance will be created as total running instances:" 
					+ currentActiveInstances
					+ " has reached max:"
					+ maxRunningInstance);
		}
		return result;
	}

	private int getQueueSize() {
		int counter = 0;
		Connection connection = null;
		try {
			connection = pooledConnectionFactory.createConnection();
			connection.start();
			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			Queue tempQueue = session.createQueue(queueName);
			QueueBrowser browser = session.createBrowser(tempQueue);
			Enumeration<?> enumerator = browser.getEnumeration();
			while (enumerator.hasMoreElements()) {
				enumerator.nextElement();
				counter++;
			}
		} catch (JMSException e) {
			logger.error("Error when checking message size in queue:" + queueName, e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (JMSException e) {
					logger.error("Error in closing queue connection", e);
				}
			}
		}
		return counter;
	}

	@PreDestroy
	public void shutDown() {
	  shutDown = true;
	  if (executorService != null) {
		  executorService.shutdownNow();
	  }
	  if (pooledConnectionFactory != null) {
		  pooledConnectionFactory.stop();
	  }
	}
}
