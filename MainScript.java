import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ILoginHandler2;
import com.ibm.team.repository.client.ILoginInfo2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.client.internal.TeamRepository;
import com.ibm.team.repository.client.login.UsernameAndPasswordLoginInfo;
import com.ibm.team.repository.common.IAuditableHandle;
import com.ibm.team.repository.common.IContributorHandle;

import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.repository.common.query.IItemQuery;
import com.ibm.team.repository.common.query.IItemQueryPage;

import com.ibm.team.repository.common.service.IQueryService;
import com.ibm.team.repository.transport.client.AuthenticationException;

import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.client.internal.WorkspaceManager;

import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IWorkspaceSearchCriteria;

import com.ibm.team.workitem.client.IWorkItemClient;

import com.ibm.team.workitem.common.internal.model.query.BaseWorkItemQueryModel.WorkItemQueryModel;
import com.ibm.team.workitem.common.model.*;

import com.ibm.team.workitem.client.IAuditableClient;

import com.ibm.team.foundation.common.text.XMLString;
import com.ibm.team.links.common.ILink;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.internal.links.impl.ReferenceImpl;
import com.ibm.team.process.client.IProcessClientService;

import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.process.common.ITeamAreaHandle;


import com.ibm.team.repository.common.*;


import com.ibm.team.workitem.common.model.IWorkItemType;

import com.ibm.team.process.common.IProjectArea;




public class MainScript {

	

	static IProgressMonitor PROGRESS_MONITOR = new NullProgressMonitor();
	static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	final String url;
	final String user;
	final String password;
	final String streamName;

	WorkspaceManager workspaceMgr;
	IWorkspaceHandle workspaceHandle;
	IWorkspaceConnection workspaceConn;
	ITeamRepository repo = null;
	
	IWorkItemClient workItemClient = null;
	IProcessClientService processClient = null;
	IAuditableClient auditableClient = null;
	
	IAttribute priorityAttribute = null;
	IEnumeration<?> priorityEnumeration = null;
	
	IAttribute severityAttribute = null;
	IEnumeration<?> severityEnumeration = null;
	
	 List<IWorkItemType> workItemTypes = null;
	 List<String> workItemsTypes =null;
	 private JSONArray workItemsArray; //
	 int counter = 0;
	
	
	public static void main(String[] args) {
	    // Here, you create an args array directly (as if they were passed from the command line)
	    String[] params = {
	        "URL",  // The repository URL
	        "username",                     // The username
	        "password",                     // The password
	        "stream"                    // The stream name
	    };

	    // Run the script using the parameters
	    try {
	        MainScript script = new MainScript(params);  // Pass the params array to the constructor
	        script.execute();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}


	public MainScript(String[] args) {
		url = args[0];
		user = args[1];
		password = args[2];
		streamName = args[3];

	}

	void execute() throws Exception {
		try {
			TeamPlatform.startup();
			init();
			fetchWorkItemsFromStream();
			close();
		} catch (Throwable e) {
			e.printStackTrace();
			throw e;
		} finally {
			TeamPlatform.shutdown();
		}
	}



	private void init() throws TeamRepositoryException {
		setupRepository();
		setupWorkspaceConnection();
	}

	private void close() {
		repo.logout();
	}


	private void setupWorkspaceConnection() throws TeamRepositoryException {
		System.out.println("Connecting to the stream...");
		workspaceMgr = (WorkspaceManager) SCMPlatform.getWorkspaceManager(repo);
		IWorkspaceSearchCriteria criteria = IWorkspaceSearchCriteria.FACTORY.newInstance();
		criteria.setKind(IWorkspaceSearchCriteria.STREAMS);
		criteria.setExactName(streamName);

		List<IWorkspaceHandle> workspaceHandles = workspaceMgr.findWorkspaces(criteria, Integer.MAX_VALUE, null);
		
		if (workspaceHandles.size() != 1) {
			System.err.println("Number of found streams: " + workspaceHandles.size() + ". Expected to find 1 stream. Check if the stream name is correct.");
			System.exit(3);
		}
		
		workspaceHandle = workspaceHandles.get(0);
		workspaceConn = workspaceMgr.getWorkspaceConnection(workspaceHandle, null);
		
		System.out.println("Connected: " + streamName);
	}
	private void fetchWorkItemsFromStream() throws TeamRepositoryException {

	    IAuditableHandle ownerHandle = workspaceConn.getResolvedWorkspace().getOwner();

	    if (ownerHandle == null) {
	        throw new TeamRepositoryException("The stream is not associated with any team or project area.");
	    }

	    IProjectArea projectArea = null;

	    if (ownerHandle instanceof IProjectAreaHandle) {
	       
	        projectArea = (IProjectArea) repo.itemManager().fetchCompleteItem((IProjectAreaHandle) ownerHandle, 0, PROGRESS_MONITOR);
	    } else if (ownerHandle instanceof ITeamAreaHandle) {
	       
	        ITeamArea teamArea = (ITeamArea) repo.itemManager().fetchCompleteItem((ITeamAreaHandle) ownerHandle, 0, PROGRESS_MONITOR);
	        projectArea = (IProjectArea) repo.itemManager().fetchCompleteItem(teamArea.getProjectArea(), 0, PROGRESS_MONITOR);
	    } else {
	        throw new TeamRepositoryException("Unsupported owner type: " + ownerHandle.getClass().getName());
	    }

	    if (projectArea == null) {
	        throw new TeamRepositoryException("Could not resolve the Project Area from the stream's owner.");
	    }

	    System.out.println("Project Area: " + projectArea.getName());

	    workItemClient = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
		processClient = (IProcessClientService) repo.getClientLibrary(IProcessClientService.class);
		auditableClient = (IAuditableClient) repo.getClientLibrary(IAuditableClient.class);
		
		 priorityAttribute = workItemClient.findAttribute(projectArea, IWorkItem.PRIORITY_PROPERTY, PROGRESS_MONITOR);
		 priorityEnumeration = workItemClient.resolveEnumeration(priorityAttribute, PROGRESS_MONITOR);
		
		 severityAttribute = workItemClient.findAttribute(projectArea, IWorkItem.SEVERITY_PROPERTY, PROGRESS_MONITOR);
		 severityEnumeration = workItemClient.resolveEnumeration(severityAttribute, PROGRESS_MONITOR);
		 workItemTypes = workItemClient.findWorkItemTypes(projectArea, PROGRESS_MONITOR);
		 workItemsArray = new JSONArray();
		 workItemsTypes = Arrays.asList(
		            "Defect",
		            "Task",
		            "Story",
		            "Epic"
		        );
		 
		 
	   
	    fetchWorkItems((TeamRepository) repo);
	    
		//getting info about workitem by id
	    IWorkItem workItem = (IWorkItem) workItemClient.findWorkItemById(Id, IWorkItem.FULL_PROFILE, PROGRESS_MONITOR);
		IWorkItemReferences workItemReferences = workItemClient.resolveWorkItemReferences(workItem, PROGRESS_MONITOR);
		getWorkItemInfo(workItem);
		  
	    saveAllWorkItemsToFile();
	
	
	
	 
	}
	
	    

	
	
	
	private void setupRepository() throws TeamRepositoryException {
		System.out.println("Setting up repository...");
		try {
			repo = TeamPlatform.getTeamRepositoryService().getTeamRepository(url);
		} catch (IllegalArgumentException e) {
			System.err.println("Getting team repository failed. Check if the URL is correct.");
			System.err.println(e.getMessage());
			System.exit(1);
		}

		System.out.println("Logging in...");
		repo.registerLoginHandler(new ILoginHandler2() {
			@Override
			public ILoginInfo2 challenge(ITeamRepository repo) {
				return new UsernameAndPasswordLoginInfo(user, password);
			}
		});

		try {
			repo.login(PROGRESS_MONITOR);
		} catch (AuthenticationException e) {
			System.err.println("Logging into the repository failed. Check if the user and password are correct.");
			System.err.println(e.getMessage());
			System.exit(2);
		}
		
		System.out.println("Logged in as " + user);
	}


	private void getWorkItemInfo(IWorkItem wi) {
	    try {
	        if (wi != null && wi.getWorkItemType()!=null && workItemsTypes.contains(getWorkItemType(wi.getWorkItemType()))) {
	        	System.out.println("Id: " + String.valueOf(wi.getId()));
	        	JSONObject workItemJson = new JSONObject();
	            
	            workItemJson.put("Id", wi.getId());
	            workItemJson.put("Tags", wi.getTags2().toString());

	            String description = getDescription(wi.getHTMLDescription());
	            workItemJson.put("Description", description);

	            String summary = getSummary(wi.getHTMLSummary());
	            workItemJson.put("Summary", summary);

	            String creationDate = getCreationDate(wi.getCreationDate());
	            workItemJson.put("CreationDate", creationDate);

	            String resolutionDate = getResolutionDate(wi.getResolutionDate());
	            workItemJson.put("ResolutionDate", resolutionDate);

	            String priority = getPriority(wi.getPriority());
	            workItemJson.put("Priority", priority);

	            String severity = getSeverity(wi.getSeverity());
	            workItemJson.put("Severity", severity);

	            String owner = getOwner(wi.getOwner());
	            workItemJson.put("Owner", owner);

	            String creator = getCreator(wi.getCreator());
	            workItemJson.put("Creator", creator);

	            String workItemType = getWorkItemType(wi.getWorkItemType());
	            workItemJson.put("WorkItemType", workItemType);

	            IWorkItemReferences workItemReferences = workItemClient.resolveWorkItemReferences(wi, PROGRESS_MONITOR);

	            List<Integer> parents = getParent(workItemReferences);
	            workItemJson.put("ParentWorkItemIDs", parents.toString());

	            List<Integer> children = getChildren(workItemReferences);
	            workItemJson.put("ChildWorkItemIDs", children.toString());
	            String downloadDir = "download_dir_path" + String.valueOf(wi.getId());
	         
	            List<String> attachments = getAttachments(workItemReferences, downloadDir);
	            // Add this work item's JSON object to the shared array
	            workItemJson.put("Attachments", attachments.toString());
	            
	            workItemJson.put("Comments", getComments(wi));
	            
	            List<Integer> related = getRelated(workItemReferences);
	            workItemJson.put("RelatedWorkItemIDs", related.toString());
	            
	            List<Integer> dependends =  getDependedOn(workItemReferences);
	            workItemJson.put("DependendsWorkItemIDs", dependends.toString());
	            
	      /*      @SuppressWarnings("unchecked")
				List<Integer> mentions = (List<Integer>) getMentions(workItemReferences).get("mentionedIds");
	            workItemJson.put("MentionsWorkItemIDs", mentions.toString());
	            
	            @SuppressWarnings("unchecked")
				List<String> mentionsUris = (List<String>) getMentions(workItemReferences).get("mentionedUris");
	            workItemJson.put("MentionsWorkItemUris", mentionsUris.toString());
	            */
	            
	            workItemsArray.add( workItemJson);
	        }
	    } catch (TeamRepositoryException e) {
	        e.printStackTrace();
	    }
	}

	
	private String getDescription(XMLString description) {
		if (description!=null) {
			return description.getPlainText();
		}
		return null;
		
	}
	
	private String getSummary(XMLString summary) {
		if (summary!=null) {
			return summary.getPlainText();
		}
		return null;
		
	}
	
	private String getCreationDate(Timestamp creationDate) {
		if(creationDate!=null) {
			return creationDate.toString();
		}
		return null;
	}
	
	private String getResolutionDate(Timestamp resolutionDate) {
		if(resolutionDate!=null) {
			return resolutionDate.toString();
		}
		return null;
	}
	
	private String getPriority(Identifier<IPriority> priority) {
		if(priority!=null) {
		    for (ILiteral literal : priorityEnumeration.getEnumerationLiterals()) {
		        if (literal.getIdentifier2().getStringIdentifier().equals(priority.getStringIdentifier())) {
		         
		            return  literal.getName();
		        }
		    }
		}
		return null;
	}
	
	private String getSeverity(Identifier<ISeverity> severity) {
		if(severity!=null) {
			  for (ILiteral literal : severityEnumeration.getEnumerationLiterals()) {
			    	
			        if (literal.getIdentifier2().getStringIdentifier().equals(severity.getStringIdentifier())) {
			     
			            return  literal.getName();
			            
			        }
			    }
		}
		return null;
	}
	
	private String getOwner( IContributorHandle contributorHandle) {   
	try {
		 IContributor  contributor = (IContributor) repo.itemManager().fetchCompleteItem(contributorHandle, IItemManager.DEFAULT, PROGRESS_MONITOR);
		if(contributor!=null) {
			return contributor.getEmailAddress();
			}
		else {
			return null;
		}
	} catch (TeamRepositoryException e) {
		return null;
		
	}
 
    }
	private String getCreator( IContributorHandle contributorHandle) {	
		try {
			 IContributor  contributor = (IContributor) repo.itemManager().fetchCompleteItem(contributorHandle, IItemManager.DEFAULT, PROGRESS_MONITOR);
		if(contributor!=null) {
			return contributor.getEmailAddress();
			}
		else {
			return null;
		}
	} catch (TeamRepositoryException e) {
		return null;
		
		}
		
	}
	private List<Integer> getParent(IWorkItemReferences iworkItemReferences) throws TeamRepositoryException {
	    List<Integer> parents = new ArrayList<>();
	    for (IReference ireference : iworkItemReferences.getReferences(WorkItemEndPoints.PARENT_WORK_ITEM)) {
	        ILink link = ireference.getLink();
	        ReferenceImpl targetReference = (ReferenceImpl) link.getTargetRef();
	        IItemHandle targetHandle = targetReference.getReferencedItem();

	        IWorkItem parent = (IWorkItem) repo.itemManager().fetchCompleteItem(targetHandle, IItemManager.DEFAULT, PROGRESS_MONITOR);

	        parents.add(parent.getId());
	    }
	    return parents;
	}
	
	private List<Integer> getDependedOn(IWorkItemReferences iworkItemReferences) throws TeamRepositoryException {
	    List<Integer> dependends = new ArrayList<>();
	    for (IReference ireference : iworkItemReferences.getReferences(WorkItemEndPoints.DEPENDS_ON_WORK_ITEM)) {
	        ILink link = ireference.getLink();
	        ReferenceImpl targetReference = (ReferenceImpl) link.getSourceRef();
	        IItemHandle targetHandle = targetReference.getReferencedItem();

	        IWorkItem dependend = (IWorkItem) repo.itemManager().fetchCompleteItem(targetHandle, IItemManager.DEFAULT, PROGRESS_MONITOR);

	        dependends.add(dependend.getId());
	    }
	    return dependends;
	}
	
	private JSONArray getComments(IWorkItem workItem) {
		JSONArray commentsArray = new JSONArray();

		for (IComment comment : workItem.getComments().getContents()) {
		    JSONObject commentJson = new JSONObject();
		    String commentContent = comment.getHTMLContent().getPlainText();
		    String commentCreator = getCreator(comment.getCreator());


		    commentJson.put("Creator", commentCreator);
		    commentJson.put("Content", commentContent);


		    commentsArray.add(commentJson);
		}
		return commentsArray;

	}
	
	private String getWorkItemType(String workItemTypeId ) {
	    for (IWorkItemType type : workItemTypes) {
	        if (type.getIdentifier().equals(workItemTypeId)) {

	          return type.getDisplayName();
	        }
	    }
	    return null;
		
	}
	private List<Integer> getChildren(IWorkItemReferences  iworkItemReferences) {   
		List<Integer> children = new ArrayList<>();
	    for (IReference ireference: iworkItemReferences.getReferences(WorkItemEndPoints.CHILD_WORK_ITEMS)){
	    	ILink link = ireference.getLink();
	    	
	
	    	ReferenceImpl target = (ReferenceImpl) link.getSourceRef();
	    	 IItemHandle targetHan = target.getReferencedItem();
	    	 IWorkItem child;
			try {
				child = (IWorkItem) repo.itemManager().fetchCompleteItem(targetHan, IItemManager.DEFAULT, PROGRESS_MONITOR);
				children.add(child.getId());
			} catch (TeamRepositoryException e) {
				e.printStackTrace();
			}

	    	
	    }
		return children;
		
	}
	private  Map<String, List<?>> getMentions(IWorkItemReferences  iworkItemReferences) {   
		List<Integer> mentioned = new ArrayList<>();
		List<String> mentionedUris = new ArrayList<>();
		
	    for (IReference ireference: iworkItemReferences.getReferences(WorkItemEndPoints.MENTIONS)){
	    	ILink link = ireference.getLink();
	    	
	    	
	    	ReferenceImpl tar = (ReferenceImpl) link.getTargetRef();
	    	if(tar.getUri().isEmpty()){
	    	 IItemHandle tarHan = tar.getReferencedItem();
	    	 System.out.println(tar.getUri());
	    	 IWorkItem mention;
			try {
				mention = (IWorkItem) repo.itemManager().fetchCompleteItem(tarHan, IItemManager.DEFAULT, PROGRESS_MONITOR);
				mentioned.add(mention.getId());
				
			} catch (TeamRepositoryException e) {
				e.printStackTrace();
				}
	    	}
	    	else {
	    		mentionedUris.add(tar.getUri());
	    	}
	    	
	    }
	    Map<String, List<?>> result = new HashMap<>();
	    result.put("mentionedIds", mentioned);
	    result.put("mentionedUris", mentionedUris);
	    return result;

		
	}
	private List<Integer> getRelated(IWorkItemReferences  iworkItemReferences) {   
		List<Integer> related = new ArrayList<>();
	    for (IReference ireference: iworkItemReferences.getReferences(WorkItemEndPoints.RELATED_WORK_ITEM)){
	    	ILink link = ireference.getLink();
	    	
	
	    	ReferenceImpl target = (ReferenceImpl) link.getTargetRef();
	    	 IItemHandle targetHan = target.getReferencedItem();
	    	 IWorkItem relate;
			try {
				relate = (IWorkItem) repo.itemManager().fetchCompleteItem(targetHan, IItemManager.DEFAULT, PROGRESS_MONITOR);
				related.add(relate.getId());

			} catch (TeamRepositoryException e) {
				e.printStackTrace();
			}

	    	
	    }
		return related;
		
	}
	
	public void saveAllWorkItemsToFile() {
	    try (FileWriter file = new FileWriter("all_work_items.json")) {
	        file.write(workItemsArray.toString()); 
	        System.out.println("All work items information saved to: all_work_items.json");
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}
	
	private List<String> getAttachments(IWorkItemReferences workItemReferences, String downloadDirectory){
		 List<String> downloadedFiles = new ArrayList<>();
		 	
		    List<IReference> attachmentReferences = workItemReferences.getReferences(WorkItemEndPoints.ATTACHMENT);
		    for (IReference reference : attachmentReferences) {
		        if (reference.isItemReference()) {
		            IItemHandle attachmentHandle = (IItemHandle) reference.resolve();

		            try {
		                IAttachment attachment = (IAttachment) repo.itemManager().fetchCompleteItem(attachmentHandle, IItemManager.DEFAULT, PROGRESS_MONITOR);
		                File dir = new File(downloadDirectory);

			            if (!dir.exists()) {
			               dir.mkdirs();
			            }

		                String attachmentName = attachment.getName();
		                downloadedFiles.add(attachmentName);
		                System.out.println("Attachment Name: " + attachmentName);

		                File outputFile = new File(downloadDirectory, attachmentName);

		                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
		                    repo.contentManager().retrieveContent(attachment.getContent(), fos, PROGRESS_MONITOR);
		                    System.out.println("Attachment downloaded to: " + outputFile.getAbsolutePath());
		                    downloadedFiles.add(attachmentName);
		                } catch (IOException e) {
		                    System.err.println("Error writing to file: " + e.getMessage());
		                }
		            } catch (TeamRepositoryException e) {
		                System.err.println("Error fetching attachment: " + e.getMessage());
		            }
		        } 
		    }
		
		    return downloadedFiles;
		}
	
	public void fetchWorkItems(TeamRepository teamRepository) throws TeamRepositoryException {

	    final IItemQuery query = IItemQuery.FACTORY.newInstance(WorkItemQueryModel.ROOT);

	    final IQueryService queryService = teamRepository.getQueryService();

	    IItemQueryPage queryPage = queryService.queryItems(query, new Object[] {}, IQueryService.DATA_QUERY_MAX_PAGE_SIZE);

		
		/*  Iterative approach
		do {

	        final List<?> handles = queryPage.getItemHandles();
	        System.out.println("Total Work Items Found: " + handles.size());

	        if (!handles.isEmpty()) {
	           
	            for (Object handleObj : handles) {
	                IWorkItemHandle workItemHandle = (IWorkItemHandle) handleObj;
	                IWorkItem item = (IWorkItem) repo.itemManager().fetchCompleteItem(workItemHandle, IItemManager.DEFAULT, PROGRESS_MONITOR);
	                counter++;
	                System.out.println("Counter: " + String.valueOf(counter));
	                getWorkItemInfo(item);
	            }
	        }

	        queryPage = (IItemQueryPage) queryService.fetchNextPage(queryPage);

	   } while (queryPage.hasNext());
	   */
	    List<IWorkItemHandle> allHandles = new ArrayList<>();

	    int counter = 0;

	    do {

	        final List<?> handles = queryPage.getItemHandles();
	        for (Object handleObj : handles) {
	            allHandles.add((IWorkItemHandle) handleObj);
	            System.out.println(counter);
	            counter++;
	        }

	        queryPage = (IItemQueryPage) queryService.fetchNextPage(queryPage);
	    } while (queryPage.hasNext());

	    System.out.println("Total Work Item Handles Found: " + allHandles.size());
	    
	    
	    int numThreads = Runtime.getRuntime().availableProcessors();
	    ExecutorService executor = Executors.newFixedThreadPool(numThreads);


	    int chunkSize = (int) Math.ceil((double) allHandles.size() / numThreads);
	    AtomicInteger counter2 = new AtomicInteger(0);

	    List<Future<?>> futures = new ArrayList<>();
	    for (int i = 0; i < allHandles.size(); i += chunkSize) {
	        final List<IWorkItemHandle> chunk = allHandles.subList(i, Math.min(i + chunkSize, allHandles.size()));
	        futures.add(executor.submit(() -> processWorkItemChunk(chunk, counter2)));
	    }


	    for (Future<?> future : futures) {
	        try {
	            future.get();
	        } catch (InterruptedException | ExecutionException e) {
	            e.printStackTrace();
	        }
	    }


	    executor.shutdown();
	  
	}
	
	private void processWorkItemChunk(List<IWorkItemHandle> chunk, AtomicInteger counter) {
	    for (IWorkItemHandle workItemHandle : chunk) {
	        try {
	            IWorkItem item = (IWorkItem) repo.itemManager().fetchCompleteItem(workItemHandle, IItemManager.DEFAULT, PROGRESS_MONITOR);
	            getWorkItemInfo(item); 

	            int currentCount = counter.incrementAndGet();
	            System.out.println("Processed Work Items: " + currentCount);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	
}
}
