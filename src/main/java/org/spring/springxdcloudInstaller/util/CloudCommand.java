package org.spring.springxdcloudInstaller.util;

import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Iterator;

import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.sshj.config.SshjSshClientModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

public class CloudCommand {
	public static ComputeService setupService(String name, String password){
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

	public  static String getSingleNodeScripts(){
		String script = new ScriptBuilder()
		.addStatement(
				exec("wget -P /home/ubuntu http://repo.springsource.org/libs-snapshot-local/org/springframework/xd/spring-xd/1.0.0.BUILD-SNAPSHOT/spring-xd-1.0.0.BUILD-20131009.122952-1.zip"))
		.addStatement(
				exec("unzip /home/ubuntu/spring-xd-1.0.0.BUILD-20131009.122952-1.zip -d /home/ubuntu"))
		.addStatement(exec("/etc/init.d/redis-server start"))
		.addStatement(exec("/etc/init.d/rabbitmq-server start"))
		.addStatement(
				exec("/home/ubuntu/spring-xd-1.0.0.BUILD-SNAPSHOT/xd/bin/xd-singlenode &"))
		.render(OsFamily.UNIX);
		return script;
	}
	
	public static String getAdminScripts(){
		String script = new ScriptBuilder()
		.addStatement(
				exec("wget -P /home/ubuntu http://repo.springsource.org/libs-snapshot-local/org/springframework/xd/spring-xd/1.0.0.BUILD-SNAPSHOT/spring-xd-1.0.0.BUILD-20131009.122952-1.zip"))
		.addStatement(
				exec("unzip /home/ubuntu/spring-xd-1.0.0.BUILD-20131009.122952-1.zip -d /home/ubuntu"))
		.addStatement(exec("/etc/init.d/redis-server start"))
		.addStatement(exec("/etc/init.d/rabbitmq-server start"))
		.addStatement(
				exec("/home/ubuntu/spring-xd-1.0.0.BUILD-SNAPSHOT/xd/bin/xd-admin &"))
		.render(OsFamily.UNIX);
		return script;
	}

	public static String getContainerScripts(){
		String script = new ScriptBuilder()
		.addStatement(
				exec("wget -P /home/ubuntu http://repo.springsource.org/libs-snapshot-local/org/springframework/xd/spring-xd/1.0.0.BUILD-SNAPSHOT/spring-xd-1.0.0.BUILD-20131009.122952-1.zip"))
		.addStatement(
				exec("unzip /home/ubuntu/spring-xd-1.0.0.BUILD-20131009.122952-1.zip -d /home/ubuntu"))
		.addStatement(
				exec("/home/ubuntu/spring-xd-1.0.0.BUILD-SNAPSHOT/xd/bin/xd-container &"))
		.render(OsFamily.UNIX);
		return script;
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
