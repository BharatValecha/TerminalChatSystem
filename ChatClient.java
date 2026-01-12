import java.io.*;
import java.net.*;
import java.util.*;

public class ChatClient {
    private static volatile boolean running = true;
    private static volatile String lastServerResponse = null;
    private static final Object responseLock = new Object();
    
   
    private static volatile String currentChatContext = "MAIN_MENU"; 
    private static volatile String currentChatTarget = null;
    
    public static void main(String[] args) throws IOException {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Testing database connection...");
        if (!DatabaseManager.testConnection()) {
            System.out.println(">>> ERROR: Could not connect to database. Check MySQL & DB_HOST.");
            return;
        }
        System.out.println(">>> Database is reachable.\n");

        String username = null;
        boolean authenticated = false;

        while (!authenticated) {
            System.out.println("==== Chat Application ====");
            System.out.println("1. Login");
            System.out.println("2. Sign Up");
            System.out.println("0. Exit");
            System.out.print("Select option: ");
            String authChoice = console.readLine();
            if (authChoice == null || authChoice.trim().equals("0")) {
                System.out.println("Bye.");
                return;
            }

            switch (authChoice.trim()) {
                case "1": // login
                    System.out.print("Username: ");
                    String u = console.readLine();
                    System.out.print("Password: ");
                    String p = console.readLine();
                    if (DatabaseManager.authenticateUser(u, p)) {
                        username = u.trim();
                        authenticated = true;
                        System.out.println(">>> Login successful. Welcome, " + username + "!\n");
                    } else {
                        System.out.println(">>> Invalid username or password.\n");
                    }
                    break;

                case "2":  
                    System.out.print("Choose username: ");
                    String su = console.readLine();
                    if (su == null || su.trim().isEmpty()) {
                        System.out.println(">>> Username cannot be empty.\n");
                        break;
                    }
                    su = su.trim();
                    if (DatabaseManager.userExists(su)) {
                        System.out.println(">>> Username already taken.\n");
                        break;
                    }
                    System.out.print("Choose password (min 6 chars): ");
                    String sp = console.readLine();
                    if (sp == null || sp.length() < 6) {
                        System.out.println(">>> Password too short.\n");
                        break;
                    }
                    System.out.print("Confirm password: ");
                    String cp = console.readLine();
                    if (!sp.equals(cp)) {
                        System.out.println(">>> Passwords do not match.\n");
                        break;
                    }
                    if (DatabaseManager.registerUser(su, sp)) {
                        System.out.println(">>> Signup successful. You can now login.\n");
                    } else {
                        System.out.println(">>> Signup failed. Try again.\n");
                    }
                    break;

                default:
                    System.out.println(">>> Invalid option.\n");
            }
        }

        System.out.print("Enter server IP address (or press Enter for localhost): ");
        String hostInput = console.readLine();
        String host = (hostInput == null || hostInput.trim().isEmpty()) ? "localhost" : hostInput.trim();

        System.out.print("Enter server port (or press Enter for 12345): ");
        String portInput = console.readLine();
        int port = 12345;
        if (portInput != null && !portInput.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portInput.trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid port, using default 12345");
            }
        }

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);

        File userFolder = new File(username);
        if (!userFolder.exists()) userFolder.mkdirs();

        Socket socket;
        try {
            socket = new Socket(host, port);
            System.out.println("Connected to server " + host + ":" + port);
        } catch (IOException e) {
            System.out.println("Could not connect to server at " + host + ":" + port);
            System.out.println("Error: " + e.getMessage());
            return;
        }

        BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);

        serverOut.println(username);
  
        String finalUsername = username;
        new Thread(() -> {
            try {
                String msg;
                FileWriter fw = null;
                String currentIncomingFile = null;
                
                while ((msg = serverIn.readLine()) != null) {
                    if (msg.startsWith("INFO ")) {
                        continue;
                    } else if (msg.startsWith("USERS ") || msg.startsWith("GROUP_MEMBERS ")) {
                        synchronized (responseLock) {
                            lastServerResponse = msg;
                            responseLock.notify();
                        }
                    } else if (msg.startsWith("GROUPS ")) {
                        synchronized (responseLock) {
                            lastServerResponse = msg;
                            responseLock.notify();
                        }
                    } else if (msg.startsWith("PRIVATE_FROM ")) {
                        String rest = msg.substring("PRIVATE_FROM ".length());
                        int sep = rest.indexOf(" : ");
                        if (sep > 0) {
                            String from = rest.substring(0, sep);
                            String text = rest.substring(sep + 3);
                            
                            if (!currentChatContext.equals("CHAT_WITH_USER") || 
                                !from.equals(currentChatTarget)) {
                                System.out.println("\n>>> [1-to-1 chat from " + from + "]");
                            }
                            System.out.println("[" + from + "]: " + text);
                        } else {
                            System.out.println(msg);
                        }
                    } else if (msg.startsWith("GROUP_FROM ")) {
                        String rest = msg.substring("GROUP_FROM ".length());
                        int sep = rest.indexOf(" : ");
                        if (sep > 0) {
                            String from = rest.substring(0, sep);
                            String text = rest.substring(sep + 3);
                            System.out.println("[" + from + "]: " + text);
                        } else {
                            System.out.println(msg);
                        }
                    } else if (msg.startsWith("GROUP_INFO ")) {
                        System.out.println(">>> " + msg.substring("GROUP_INFO ".length()));
                    } else if (msg.startsWith("FILESTART_USER ")) {
                        String[] parts = msg.split(" ", 3);
                        if (parts.length >= 3) {
                            String sender = parts[1];
                            String fileName = parts[2];
                            currentIncomingFile = fileName;
                            File f = new File(userFolder, "received_" + fileName);
                            fw = new FileWriter(f);
                            System.out.println(">>> Receiving file '" + fileName + "' from " + sender);
                        }
                    } else if (msg.startsWith("FILESTART_GROUP ")) {
                        String[] parts = msg.split(" ", 4);
                        if (parts.length >= 4) {
                            String sender = parts[1];
                            String groupName = parts[2];
                            String fileName = parts[3];
                            currentIncomingFile = fileName;
                            File f = new File(userFolder, "received_" + fileName);
                            fw = new FileWriter(f);
                            System.out.println(">>> Receiving file '" + fileName + "' from " + sender +
                                    " in group " + groupName);
                        }
                    } else if (msg.startsWith("FILEDATA ")) {
                        if (fw != null) {
                            fw.write(msg.substring(9) + "\n");
                        }
                    } else if (msg.equals("FILEEND")) {
                        if (fw != null) {
                            fw.close();
                            fw = null;
                            System.out.println(">>> File '" + currentIncomingFile + "' received successfully");
                            currentIncomingFile = null;
                        }
                    } else if (msg.startsWith("ERROR ")) {
                        System.out.println(">>> " + msg);
                    } else if (msg.startsWith("OK ")) {
                        synchronized (responseLock) {
                            lastServerResponse = msg;
                            responseLock.notify();
                        }
                        if (msg.contains("File sent")) {
                            System.out.println(">>> " + msg.substring(3));
                        }
                    } else {
                        System.out.println(msg);
                    }
                }
            } catch (IOException e) {
                if (running) System.out.println(">>> Disconnected from server");
            }
        }).start();
        
        while (running) {
            currentChatContext = "MAIN_MENU";
            currentChatTarget = null;
            
            System.out.println();
            System.out.println("==== MAIN MENU ====");
            System.out.println("1. 1-to-1 chat");
            System.out.println("2. Group chat (channels)");
            System.out.println("0. Exit");
            System.out.print("Select option: ");
            
            String choice = console.readLine();
            if (choice == null) break;
            
            switch (choice.trim()) {
                case "1":
                    oneToOneMenu(console, serverOut, username, userFolder);
                    break;
                case "2":
                    groupMenu(console, serverOut, username, userFolder);
                    break;
                case "0":
                    running = false;
                    break;
                default:
                    System.out.println(">>> Invalid option");
            }
        }
        
        try {
            socket.close();
        } catch (IOException ignored) {}
        System.out.println("Bye.");
    }
    
    
    private static void oneToOneMenu(BufferedReader console,
                                      PrintWriter serverOut,
                                      String username,
                                      File userFolder) throws IOException {
        while (true) {
            currentChatContext = "MAIN_MENU";
            currentChatTarget = null;
            
            serverOut.println("LIST_USERS");
            
            String line = waitForResponse();
            if (line == null) {
                System.out.println(">>> Server disconnected.");
                return;
            }
            
            List<String> users = new ArrayList<>();
            if (line.startsWith("USERS ")) {
                String payload = line.substring(6).trim();
                if (!payload.isEmpty()) {
                    String[] parts = payload.split(",");
                    for (String p : parts) {
                        if (!p.equals(username)) { 
                            users.add(p);
                        }
                    }
                }
            } else {
                System.out.println(line);
            }
            
            System.out.println();
            System.out.println("==== 1-to-1 Chat ====");
            if (users.isEmpty()) {
                System.out.println("No other users online.");
                System.out.println("0. Back to previous menu");
                System.out.print("Select option: ");
                String inp = console.readLine();
                if (inp == null || inp.trim().equals("0")) return;
                continue;
            } else {
                int idx = 1;
                for (String u : users) {
                    System.out.println(idx + ". " + u);
                    idx++;
                }
                System.out.println("0. Back to previous menu");
                
                System.out.print("Select option: ");
                String sel = console.readLine();
                if (sel == null) return;
                sel = sel.trim();
                if (sel.equals("0")) return;
                
                int num;
                try {
                    num = Integer.parseInt(sel);
                } catch (NumberFormatException e) {
                    System.out.println(">>> Invalid selection");
                    continue;
                }
                
                if (num < 1 || num > users.size()) {
                    System.out.println(">>> Invalid selection");
                    continue;
                }
                
                String targetUser = users.get(num - 1);
                oneToOneChatMenu(console, serverOut, username, targetUser, userFolder);
            }
        }
    }
    
    private static void oneToOneChatMenu(BufferedReader console,
                                          PrintWriter serverOut,
                                          String myName,
                                          String targetUser,
                                          File userFolder) throws IOException {
        while (true) {
            currentChatContext = "MAIN_MENU";
            currentChatTarget = null;
            
            System.out.println();
            System.out.println("==== Chat with " + targetUser + " ====");
            System.out.println("1. Send message");
            System.out.println("2. Send file");
            System.out.println("0. Back to previous menu");
            System.out.print("Select option: ");
            
            String choice = console.readLine();
            if (choice == null) return;
            choice = choice.trim();
            
            if (choice.equals("1")) {
                currentChatContext = "CHAT_WITH_USER";
                currentChatTarget = targetUser;
                
                System.out.println("-----------Message portal (type '0' to exit)-----------");
                while (true) {
                    String msg = console.readLine();
                    if (msg == null) return;
                    if (msg.equals("0")) break;
                    if (!msg.trim().isEmpty()) {
                        serverOut.println("PRIVMSG " + targetUser + " :" + msg);
                        System.out.println("[" + myName + "]: " + msg);
                    }
                }
                
                currentChatContext = "MAIN_MENU";
                currentChatTarget = null;
                
            } else if (choice.equals("2")) {
                System.out.print("Enter file name (inside folder " + userFolder.getName() + "): ");
                String fileName = console.readLine();
                if (fileName == null || fileName.trim().isEmpty()) {
                    System.out.println(">>> File name cannot be empty.");
                    continue;
                }
                
                fileName = fileName.trim();
                File f = new File(userFolder, fileName);
                if (!f.exists() || !f.isFile()) {
                    System.out.println(">>> File not found: " + f.getAbsolutePath());
                    continue;
                }
                
                serverOut.println("SENDFILE_USER " + targetUser + " " + fileName);
                try (BufferedReader fr = new BufferedReader(new FileReader(f))) {
                    String l;
                    while ((l = fr.readLine()) != null) {
                        serverOut.println(l);
                    }
                }
                serverOut.println("FILEEND");
                System.out.println(">>> Sending file...");
            } else if (choice.equals("0")) {
                return;
            } else {
                System.out.println(">>> Invalid option");
            }
        }
    }
    
    
    private static void groupMenu(BufferedReader console,
                                   PrintWriter serverOut,
                                   String username,
                                   File userFolder) throws IOException {
        while (true) {
            currentChatContext = "MAIN_MENU";
            currentChatTarget = null;
            
            System.out.println();
            System.out.println("==== Group Chat (Channels) ====");
            System.out.println("1. GroupA");
            System.out.println("2. GroupB");
            System.out.println("3. GroupC");
            System.out.println("0. Back to previous menu");
            System.out.print("Select option: ");
            
            String sel = console.readLine();
            if (sel == null) return;
            sel = sel.trim();
            if (sel.equals("0")) return;
            
            String groupName = null;
            if (sel.equals("1")) groupName = "GroupA";
            else if (sel.equals("2")) groupName = "GroupB";
            else if (sel.equals("3")) groupName = "GroupC";
            else {
                System.out.println(">>> Invalid option");
                continue;
            }
            
            serverOut.println("JOIN_GROUP " + groupName);
            String joinResp = waitForResponse();
            if (joinResp != null) System.out.println(">>> " + joinResp);
            
            serverOut.println("LIST_GROUP_MEMBERS");
            String membersResp = waitForResponse();
            if (membersResp != null && membersResp.startsWith("GROUP_MEMBERS ")) {
                String payload = membersResp.substring("GROUP_MEMBERS ".length()).trim();
                
                System.out.println();
                System.out.println("-----------Members in " + groupName + "-----------");
                if (payload.isEmpty()) {
                    System.out.println("(none)");
                } else {
                    String[] members = payload.split(",");
                    int idx = 1;
                    for (String member : members) {
                        System.out.println(idx + ". " + member);
                        idx++;
                    }
                }
            }
            
            groupChatMenu(console, serverOut, groupName, username, userFolder);
        }
    }
    
    private static void groupChatMenu(BufferedReader console,
                                       PrintWriter serverOut,
                                       String groupName,
                                       String username,
                                       File userFolder) throws IOException {
        while (true) {
            currentChatContext = "IN_GROUP";
            currentChatTarget = groupName;
            
            System.out.println();
            System.out.println("==== Channel: " + groupName + " ====");
            System.out.println("1. Send message");
            System.out.println("2. Send file");
            System.out.println("0. Back to previous menu");
            System.out.print("Select option: ");
            
            String choice = console.readLine();
            if (choice == null) return;
            choice = choice.trim();
            
            if (choice.equals("1")) {
                System.out.println("-----------Message portal (type '0' to exit)-----------");
                while (true) {
                    String msg = console.readLine();
                    if (msg == null) return;
                    if (msg.equals("0")) break;
                    if (!msg.trim().isEmpty()) {
                        serverOut.println("GROUPMSG :" + msg);
                        
                    }
                }
            } else if (choice.equals("2")) {
                System.out.print("Enter file name (inside folder " + userFolder.getName() + "): ");
                String fileName = console.readLine();
                if (fileName == null || fileName.trim().isEmpty()) {
                    System.out.println(">>> File name cannot be empty.");
                    continue;
                }
                
                fileName = fileName.trim();
                File f = new File(userFolder, fileName);
                if (!f.exists() || !f.isFile()) {
                    System.out.println(">>> File not found: " + f.getAbsolutePath());
                    continue;
                }
                
                serverOut.println("SENDFILE_GROUP " + groupName + " " + fileName);
                try (BufferedReader fr = new BufferedReader(new FileReader(f))) {
                    String l;
                    while ((l = fr.readLine()) != null) {
                        serverOut.println(l);
                    }
                }
                serverOut.println("FILEEND");
                System.out.println(">>> Sending file to channel...");
            } else if (choice.equals("0")) {
                currentChatContext = "MAIN_MENU";
                currentChatTarget = null;
                return;
            } else {
                System.out.println(">>> Invalid option");
            }
        }
    }
    
    private static String waitForResponse() {
        synchronized (responseLock) {
            lastServerResponse = null;
            try {
                responseLock.wait(5000); 
                return lastServerResponse;
            } catch (InterruptedException e) {
                return null;
            }
        }
    }
}
