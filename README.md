# rtc-workitem-fetcher
A script to download work item data from IBM Rational Team Concert (RTC) Jazz servers, enabling seamless access to project tracking information for analysis and reporting.

# MainScript

This application connects to a team repository, fetches work items from a specified stream, and processes them into a structured JSON format. It interacts with a repository, stream, and project area to extract work item data, including attributes like priority, severity, attachments, and relationships.

---

## Prerequisites

1. **Java Environment**: Ensure that you have Java installed on your system.
2. **Dependencies**:
   - RTC (Rational Team Concert) SDK libraries.
3. **Access**:
   - A valid team repository URL.
   - A user account with appropriate permissions to log in, access streams, and fetch work items.

---

## Usage

### Arguments

The script requires four arguments to run:

1. **URL**: The URL of the repository (e.g., `https://repository.example.com`).
2. **Username**: The username for authentication.
3. **Password**: The password for the username.
4. **Stream Name**: The name of the stream from which work items will be fetched
5. **Attachments Directory**: The path to the directory where attachments should be stored.

Example:

```bash
java -cp <path-to-jar> MainScript https://repository.example.com username password StreamName
```

---

## Methods

### 1. `MainScript(String[] args)`
Initializes the script with the provided arguments.

#### Parameters:
- `args[0]`: Repository URL  
- `args[1]`: Username  
- `args[2]`: Password  
- `args[3]`: Stream name  

---

### 2. `execute()`
Orchestrates the script's execution.

#### Steps:
1. Starts the RTC platform.
2. Calls `init()` to set up the repository and workspace connection.
3. Fetches work items using `fetchWorkItemsFromStream()`.
4. Closes the repository connection and shuts down the platform.

---

### 3. `init()`
Initializes repository and workspace connections.

#### Methods Called:
- `setupRepository()`
- `setupWorkspaceConnection()`

---

### 4. `setupRepository()`
Establishes the connection to the repository and logs in using the provided credentials.

---

### 5. `setupWorkspaceConnection()`
Connects to the specified stream in the repository.

---

### 6. `fetchWorkItemsFromStream()`
Fetches and processes work items from the stream's associated project area.

#### Steps:
1. Resolves the stream's owner (team or project area).
2. Initializes clients and attributes:
   - `IWorkItemClient`
   - `IProcessClientService`
   - Others as needed
3. Processes work items into a JSON array.

---

### 7. `getWorkItemInfo(IWorkItem wi)`
Extracts detailed information from a work item.

#### Includes:
- **General Information**: ID, description, summary, creation date, resolution date.  
- **Attributes**: Priority, severity, owner, and creator.  
- **Relationships**: Parents, children, and dependencies.  
- **Other Data**: Comments and attachments.

---

## 8. Helper Methods
Supports data extraction and transformation.

### Key Methods:
- **`getDescription(XMLString description)`**: Converts `XMLString` to plain text.
- **`getPriority(Identifier<IPriority> priority)`**: Resolves priority enumeration.
- **`getOwner(IContributorHandle contributorHandle)`**: Fetches owner details.
- **`getChildren(IWorkItemReferences references)`**: Retrieves child work items.
- **`getParent(IWorkItemReferences references)`**: Retrieves parent work item.
- **`getRelated(IWorkItemReferences references)`**: Retrieves related work items.
- **`getDependedOn(IWorkItemReferences iworkItemReferences)`**: Retrieves dependend work items.
- **`getComments(IWorkItem workItem)`**: Retrieves comments' contents and authors.
- **`getAttachments(IWorkItemReferences workItemReferences, String downloadDirectory)`** : Retrieves attachments.
