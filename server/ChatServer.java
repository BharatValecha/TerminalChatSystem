import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    private static final int PORT = 12345;

    private static final Map<String, ClientHandler> users = new ConcurrentHashMap<>();
    private static final Map<String, Set<ClientHandler>> groups = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("Server started on port " + PORT + " ...");
        ServerSocket serverSocket = new ServerSocket(PORT);

        groups.put("GroupA", ConcurrentHashMap.newKeySet());
        groups.put("GroupB", ConcurrentHashMap.newKeySet());
        groups.put("GroupC", ConcurrentHashMap.newKeySet());

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected " + clientSocket.getRemoteSocketAddress());
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    static class ClientHandler implements Runnable {

        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        private String username = null;
        private String currentGroup = null;

        // file transfer state
        private boolean sendingFile = false;
        private ClientHandler fileTargetHandler = null; 
        private String fileTargetGroup = null;          
        private String fileName = null;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String nameLine = in.readLine();
                if (nameLine == null || nameLine.trim().isEmpty()) {
                    socket.close();
                    return;
                }
                username = nameLine.trim();

                synchronized (users) {
                    if (users.containsKey(username)) {
                        out.println("ERROR Username already in use. Disconnecting.");
                        socket.close();
                        return;
                    }
                    users.put(username, this);
                }

                System.out.println(username + " is online");

                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (sendingFile) {
                        handleFileData(line);
                        continue;
                    }

                    if (line.equals("LIST_USERS")) {
                        handleListUsers();
                    } else if (line.startsWith("PRIVMSG ")) {
                        handlePrivMsg(line);
                    } else if (line.startsWith("SENDFILE_USER ")) {
                        handleSendFileUserStart(line);
                    } else if (line.equals("LIST_GROUPS")) {
                        handleListGroups();
                    } else if (line.startsWith("JOIN_GROUP ")) {
                        handleJoinGroup(line);
                    } else if (line.equals("LIST_GROUP_MEMBERS")) {
                        handleListGroupMembers();
                    } else if (line.startsWith("GROUPMSG ")) {
                        handleGroupMsg(line);
                    } else if (line.startsWith("SENDFILE_GROUP ")) {
                        handleSendFileGroupStart(line);
                    } else {
                        out.println("ERROR Unknown command");
                    }
                }
            } catch (IOException e) {
                System.out.println("Client IO error: " + e.getMessage());
            } finally {
                cleanup();
            }
        }


        private void handleListUsers() {
            StringBuilder sb = new StringBuilder();
            sb.append("USERS ");
            boolean first = true;
            for (String u : users.keySet()) {
                if (!first) sb.append(",");
                sb.append(u);
                first = false;
            }
            out.println(sb.toString());
        }

        private void handlePrivMsg(String line) {
            String[] parts = line.split(" ", 3);
            if (parts.length < 3 || !parts[2].startsWith(":")) {
                out.println("ERROR Usage: PRIVMSG <user> :<message>");
                return;
            }
            String targetUser = parts[1];
            String message = parts[2].substring(1);

            ClientHandler target = users.get(targetUser);
            if (target != null) {
                target.out.println("PRIVATE_FROM " + username + " : " + message);
            } else {
                out.println("ERROR User not found");
            }
        }

        private void handleSendFileUserStart(String line) {
            String[] parts = line.split(" ", 3);
            if (parts.length < 3) {
                out.println("ERROR Usage: SENDFILE_USER <user> <filename>");
                return;
            }
            String targetUser = parts[1];
            fileName = parts[2];

            ClientHandler target = users.get(targetUser);
            if (target == null) {
                out.println("ERROR User not found");
                return;
            }

            fileTargetHandler = target;
            fileTargetGroup = null;
            sendingFile = true;

            target.out.println("FILESTART_USER " + username + " " + fileName);
        }

        

        private void handleListGroups() {
            out.println("GROUPS GroupA,GroupB,GroupC");
        }

        private void handleJoinGroup(String line) {
            String groupName = line.substring("JOIN_GROUP".length()).trim();
            if (!groups.containsKey(groupName)) {
                out.println("ERROR Group not found");
                return;
            }

            
            if (currentGroup != null && groups.containsKey(currentGroup)) {
                Set<ClientHandler> oldSet = groups.get(currentGroup);
                oldSet.remove(this);
                broadcastToGroup(currentGroup,
                        "GROUP_INFO " + username + " has left the group");
            }

            currentGroup = groupName;
            groups.get(groupName).add(this);
            out.println("OK Joined group " + groupName);
            broadcastToGroup(groupName,
                    "GROUP_INFO " + username + " has joined the group");
        }

        private void handleListGroupMembers() {
            if (currentGroup == null || !groups.containsKey(currentGroup)) {
                out.println("GROUP_MEMBERS ");
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("GROUP_MEMBERS ");
            boolean first = true;
            for (ClientHandler ch : groups.get(currentGroup)) {
                if (!first) sb.append(",");
                sb.append(ch.username);
                first = false;
            }
            out.println(sb.toString());
        }

        private void handleGroupMsg(String line) {
            if (currentGroup == null || !groups.containsKey(currentGroup)) {
                out.println("ERROR Join a group first");
                return;
            }
            
            if (!line.startsWith("GROUPMSG :")) {
                out.println("ERROR Usage: GROUPMSG :<message>");
                return;
            }
            String msg = line.substring("GROUPMSG :".length());
            
            
            broadcastToGroupIncludingSender(currentGroup,
                    "GROUP_FROM " + username + " : " + msg);
        }

        private void handleSendFileGroupStart(String line) {
            
            String[] parts = line.split(" ", 3);
            if (parts.length < 3) {
                out.println("ERROR Usage: SENDFILE_GROUP <group> <filename>");
                return;
            }
            String groupName = parts[1];
            fileName = parts[2];

            if (!groups.containsKey(groupName)) {
                out.println("ERROR Group not found");
                return;
            }

            fileTargetHandler = null;
            fileTargetGroup = groupName;
            sendingFile = true;

            for (ClientHandler ch : groups.get(groupName)) {
                if (ch != this) {
                    ch.out.println("FILESTART_GROUP " + username + " " + groupName + " " + fileName);
                }
            }
        }

        private void broadcastToGroup(String groupName, String message) {
            Set<ClientHandler> members = groups.get(groupName);
            if (members == null) return;
            for (ClientHandler ch : members) {
                ch.out.println(message);
            }
        }

        private void broadcastToGroupIncludingSender(String groupName, String message) {
            Set<ClientHandler> members = groups.get(groupName);
            if (members == null) return;
            for (ClientHandler ch : members) {
                ch.out.println(message);
            }
        }


        private void handleFileData(String line) {
            if (!sendingFile) return;

            if (line.equals("FILEEND")) {
                if (fileTargetHandler != null) {
                    fileTargetHandler.out.println("FILEEND");
                    out.println("OK File sent to " + fileTargetHandler.username);
                } else if (fileTargetGroup != null && groups.containsKey(fileTargetGroup)) {
                    for (ClientHandler ch : groups.get(fileTargetGroup)) {
                        if (ch != this) {
                            ch.out.println("FILEEND");
                        }
                    }
                    out.println("OK File sent to group " + fileTargetGroup);
                }
                sendingFile = false;
                fileTargetHandler = null;
                fileTargetGroup = null;
                fileName = null;
            } else {
                if (fileTargetHandler != null) {
                    fileTargetHandler.out.println("FILEDATA " + line);
                } else if (fileTargetGroup != null && groups.containsKey(fileTargetGroup)) {
                    for (ClientHandler ch : groups.get(fileTargetGroup)) {
                        if (ch != this) {
                            ch.out.println("FILEDATA " + line);
                        }
                    }
                }
            }
        }

        private void cleanup() {
            try {
                if (username != null) {
                    users.remove(username);
                    System.out.println(username + " has gone offline");
                }
                if (currentGroup != null && groups.containsKey(currentGroup)) {
                    Set<ClientHandler> set = groups.get(currentGroup);
                    set.remove(this);
                    broadcastToGroup(currentGroup,
                            "GROUP_INFO " + username + " has left the group");
                }
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}