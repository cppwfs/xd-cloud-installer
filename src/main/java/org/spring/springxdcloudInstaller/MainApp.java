/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.spring.springxdcloudInstaller;

import static org.jclouds.ec2.options.RunInstancesOptions.Builder.asType;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.ec2.EC2AsyncClient;
import org.jclouds.ec2.EC2Client;
import org.jclouds.ec2.domain.InstanceState;
import org.jclouds.ec2.domain.InstanceType;
import org.jclouds.ec2.domain.KeyPair;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.predicates.InstanceStateRunning;
import org.jclouds.predicates.InetSocketAddressConnect;
import org.jclouds.predicates.RetryablePredicate;
import org.jclouds.rest.RestContext;
import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.sshj.config.SshjSshClientModule;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import com.google.inject.Module;

/**
 * This the Main class of an Application that installs XD onto a cloud platform.
 * 
 * Usage is: java MainApp accesskeyid secretkey command name where command in
 * create destroy
 * 
 * @author Glenn Renfro
 */
public class MainApp {

	public static int PARAMETERS = 5;
	public static String INVALID_SYNTAX = "Invalid number of parameters. Syntax is: accesskeyid secretkey command name type \nwhere command in create destroy\n type container, admin, singlenode ";

	public static void main(String[] args) throws TimeoutException {

		if (args.length < PARAMETERS)
			throw new IllegalArgumentException(INVALID_SYNTAX);

		// Args
		String accesskeyid = args[0];
		String secretkey = args[1];
		String command = args[2];
		String name = args[3];
		String type = args[4];
 
		// Init  
		RestContext<EC2Client, EC2AsyncClient> context = ContextBuilder
				.newBuilder("aws-ec2").credentials(accesskeyid, secretkey)
				.build();

		// Get a synchronous client
		EC2Client client = context.getApi();
		try {
			if (command.equals("create")) {

			KeyPair pair = createKeyPair(client, name);
				RunningInstance instance = createSecurityGroupKeyPairAndInstance(
						client, name);

				System.out.printf("instance %s ready%n", instance.getId());
				System.out.printf("ip address: %s%n", instance.getIpAddress());
				System.out.printf("dns name: %s%n", instance.getDnsName());
				System.out.printf("login identity:%n%s%n",
						pair.getKeyMaterial());
			} else if (command.equals("destroy")) {
				destroySecurityGroupKeyPairAndInstance(client, name);
			} else {
				throw new IllegalArgumentException(INVALID_SYNTAX);
			}
		} finally {
			// Close connection
			context.close();
			System.exit(0);
		}

	}

	private static void destroySecurityGroupKeyPairAndInstance(
			EC2Client client, String name) {
		try {
			String id = findInstanceByKeyName(client, name).getId();
			System.out.printf("%d: %s terminating instance%n",
					System.currentTimeMillis(), id);
			client.getInstanceServices().terminateInstancesInRegion(null,
					findInstanceByKeyName(client, name).getId());
		} catch (NoSuchElementException e) {
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			System.out.printf("%d: %s deleting keypair%n",
					System.currentTimeMillis(), name);
			client.getKeyPairServices().deleteKeyPairInRegion(null, name);
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			System.out.printf("%d: %s deleting group%n",
					System.currentTimeMillis(), name);
			client.getSecurityGroupServices().deleteSecurityGroupInRegion(null,
					name);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static RunningInstance createSecurityGroupKeyPairAndInstance(
			EC2Client client, String name) throws TimeoutException {
		System.out.println("about to run the instance.");
		RunningInstance instance = null;
		// create a new instance
		try{
		 instance = runInstance(client, "default", name);//need to allow them to pass in a specific group or at least the xd group
		 
		}
		catch(RuntimeException e){
			e.printStackTrace();
			throw e;
		}
		// await for the instance to start
		return blockUntilInstanceRunning(client, instance);
	}



	static KeyPair createKeyPair(EC2Client client, String name) {
		System.out.printf("%d: creating keypair: %s%n",
				System.currentTimeMillis(), name);
		// return client.getKeyPairServices().createKeyPairInRegion(null, name);
		return client.getKeyPairServices()
				.describeKeyPairsInRegion(null, "testpair").iterator().next();
	}

	static RunningInstance runInstance(EC2Client client,
			String securityGroupName, String keyPairName) {
		System.out.println("Base Image is initializing...  Installing XD...");
        String script = new ScriptBuilder().addStatement(exec("wget -P /home/ubuntu http://repo.springsource.org/libs-snapshot-local/org/springframework/xd/spring-xd/1.0.0.BUILD-SNAPSHOT/spring-xd-1.0.0.BUILD-20131009.122952-1.zip"))
      		  .addStatement(exec("unzip /home/ubuntu/spring-xd-1.0.0.BUILD-20131009.122952-1.zip -d /home/ubuntu"))
      		  .addStatement(exec("/etc/init.d/redis-server start"))
      		  .addStatement(exec("/etc/init.d/rabbitmq-server start"))
      		  .addStatement(exec("/home/ubuntu/spring-xd-1.0.0.BUILD-SNAPSHOT/xd/bin/xd-singlenode &"))
      		  .render(OsFamily.UNIX);

		System.out.printf("%d: running instance%n", System.currentTimeMillis());
 
		Reservation<? extends RunningInstance> reservation = client
				.getInstanceServices().runInstancesInRegion(null, null, 
						"ami-3f134156", // XD Basic Image.
						1, // minimum instances
						1, // maximum instances
						asType(InstanceType.M1_SMALL) // smallest instance size
								.withKeyName(keyPairName) // key I created above
								.withSecurityGroup(securityGroupName) // group I
																		// created
																		// above
								.withUserData(script.getBytes())); // script to
																	// run as
						 											// root
		return Iterables.getOnlyElement(reservation);

	}

	static RunningInstance blockUntilInstanceRunning(EC2Client client,
			RunningInstance instance) throws TimeoutException {
		// create utilities that wait for the instance to finish
		RetryablePredicate<RunningInstance> runningTester = new RetryablePredicate<RunningInstance>(
				new InstanceStateRunning(client), 180, 5, TimeUnit.SECONDS);

		System.out.printf("%d: %s awaiting instance to run %n",
				System.currentTimeMillis(), instance.getId());
		if (!runningTester.apply(instance))
			throw new TimeoutException("timeout waiting for instance to run: "
					+ instance.getId());
 
		instance = findInstanceById(client, instance.getId());

		RetryablePredicate<HostAndPort> socketTester = new RetryablePredicate<HostAndPort>(
				new InetSocketAddressConnect(), 300, 1, TimeUnit.SECONDS);
		System.out.printf("%d: %s awaiting ssh service to start%n",
				System.currentTimeMillis(), instance.getIpAddress());
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				22)))
			throw new TimeoutException("timeout waiting for ssh to start: "
					+ instance.getIpAddress());

		System.out.printf("%d: %s ssh service started%n",
				System.currentTimeMillis(), instance.getIpAddress());

		System.out.printf("%d: %s awaiting Redis service to start%n",
				System.currentTimeMillis(), instance.getIpAddress());
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				6379)))
			throw new TimeoutException("timeout waiting for http to start: "
					+ instance.getIpAddress());

		System.out.printf("%d: %s http service started%n",
				System.currentTimeMillis(), instance.getIpAddress());
		return instance;
	}

	private static RunningInstance findInstanceById(EC2Client client,
			String instanceId) {
		// search my account for the instance I just created
		Set<? extends Reservation<? extends RunningInstance>> reservations = client
				.getInstanceServices().describeInstancesInRegion(null,
						instanceId); // last parameter (ids) narrows the
		// search

		// since we refined by instanceId there should only be one instance
		return Iterables.getOnlyElement(Iterables.getOnlyElement(reservations));
	}

	private static RunningInstance findInstanceByKeyName(EC2Client client,
			final String keyName) {
		// search my account for the instance I just created
		Set<? extends Reservation<? extends RunningInstance>> reservations = client
				.getInstanceServices().describeInstancesInRegion(null);

		// extract all the instances from all reservations
		Set<RunningInstance> allInstances = Sets.newHashSet();
		for (Reservation<? extends RunningInstance> reservation : reservations) {
			allInstances.addAll(reservation);
		}

		// get the first one that has a keyname matching what I just created
		return Iterables.find(allInstances, new Predicate<RunningInstance>() {

			public boolean apply(RunningInstance input) {
				return input.getKeyName().equals(keyName)
						&& input.getInstanceState() != InstanceState.TERMINATED;
			}

		});
	}

	private static ComputeService setupService(String name, String password){
	      Iterable<Module> modules = ImmutableSet.<Module> of(new SshjSshClientModule());

	      // These properties control how often jclouds polls for a status udpate
	      ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
	    		  .credentials(name, password) // key I created above
	            .modules(modules)
	            .buildView(ComputeServiceContext.class);
	      ComputeService computeService = context.getComputeService();
	      Iterator nodes = computeService.listNodes().iterator();
	      while (nodes.hasNext()){
	    	  org.jclouds.compute.domain.internal.NodeMetadataImpl data = (org.jclouds.compute.domain.internal.NodeMetadataImpl)nodes.next(); 
		      System.out.println(data.getClass().getName());
		      System.out.println(data.getHostname());
		      System.out.println(data.toString());
		      if(data.getHostname() !=null){
		          String script = new ScriptBuilder().addStatement(exec("wget -P /home/ubuntu http://repo.springsource.org/libs-snapshot-local/org/springframework/xd/spring-xd/1.0.0.BUILD-SNAPSHOT/spring-xd-1.0.0.BUILD-20131009.122952-1.zip"))
		        		  .addStatement(exec("unzip /home/ubuntu/spring-xd-1.0.0.BUILD-20131009.122952-1.zip -d /home/ubuntu"))
		        		  .addStatement(exec("/home/ubuntu/spring-xd-1.0.0.BUILD-SNAPSHOT/xd/bin/xd-singlenode &"))
		        		  .render(OsFamily.UNIX);
		           try{
		            RunScriptOptions options = RunScriptOptions.Builder.blockOnComplete(true).overrideLoginUser("ubuntu").overrideLoginPrivateKey(getTestPair());
		            options.runAsRoot(false);
		            ExecResponse resp = computeService.runScriptOnNode(data.getId(), script, options);
		            System.out.println(resp.getOutput());
		            System.out.println(resp.getError());
		            System.out.println(resp.getExitStatus());
		           }catch(Exception re){
		        	   re.printStackTrace();
		           }
		      }

	      }
	return computeService;	
	}
	private static String getTestPair(){
		String result = "";
		BufferedReader br= null;
		try{
			br = new BufferedReader(new FileReader("/Users/renfrg/ec2/testpair.pem"));
			while(br.ready()){
				result+=br.readLine()+"\n";
			}
		}catch(Exception ex){
			ex.printStackTrace();
		}finally{
			if(br != null){
				try{
					br.close();
				}catch(Exception ex){
					ex.printStackTrace();
				}
			}
		}
		System.out.println(result);
		return result;
	}
}
