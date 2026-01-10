# Java LAN Chat Application with MySQL Login

A console-based Java chat application that supports:

- 1-to-1 private messaging
- Group channels (GroupA, GroupB, GroupC)
- Text and file transfer
- User signup & login backed by a shared MySQL database
- Multiple clients on different devices on the same LAN

---

## Features

- **Multi-user chat server** using Java sockets (`ServerSocket`, `Socket`)
- **Concurrent clients** via per-client handler threads
- **1-to-1 chat** with user list and clean message layout
- **Group chat channels** with member listing
- **File transfer** in 1-to-1 and group chats
- **MySQL-backed authentication** (sign up & login) using JDBC + BCrypt hashing
- Designed to work across **multiple devices on the same network**

---

## Project Structure

chat-project/
ChatServer.java # Main TCP chat server (multi-client, groups, file relay)
ChatClient.java # Console client with menus + DB-based login/signup
DatabaseManager.java # JDBC + BCrypt helper for MySQL (signup/login)
lib/
mysql-connector-j-9.5.0.jar # MySQL JDBC driver
jbcrypt-0.4.jar # BCrypt password hashing
user-folders/ # (created at runtime, not committed)

text

You should not commit `.class` files or the `lib/` JARs if you want a clean repo.

---

## Prerequisites

- Java 11+ (JDK) installed (`java -version`, `javac -version`)
- MySQL 8+ installed and running
- MySQL JDBC driver JAR (e.g. `mysql-connector-j-9.5.0.jar`)
- jBCrypt JAR (e.g. `jbcrypt-0.4.jar`)
- All three `.java` files:
  - `ChatServer.java`
  - `ChatClient.java`
  - `DatabaseManager.java`

---

## Database Setup

1. Start MySQL and log in as root (or another admin):

```bash
mysql -u root -p
Create the database, users table, and dedicated application user:

sql
CREATE DATABASE chat_app;
USE chat_app;

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL
);

CREATE USER 'chatapp'@'localhost' IDENTIFIED BY 'chatapp123';
CREATE USER 'chatapp'@'%' IDENTIFIED BY 'chatapp123';
GRANT ALL PRIVILEGES ON chat_app.* TO 'chatapp'@'localhost';
GRANT ALL PRIVILEGES ON chat_app.* TO 'chatapp'@'%';
FLUSH PRIVILEGES;
If you hit the "Public Key Retrieval is not allowed" error, either:

Add allowPublicKeyRetrieval=true to the JDBC URL (see below), or

Switch the user to mysql_native_password:

sql
ALTER USER 'chatapp'@'localhost'
  IDENTIFIED WITH mysql_native_password BY 'chatapp123';
ALTER USER 'chatapp'@'%'
  IDENTIFIED WITH mysql_native_password BY 'chatapp123';
FLUSH PRIVILEGES;
DatabaseManager Configuration
In DatabaseManager.java, configure:

java
private static final String DB_HOST = "localhost"; // or server LAN IP, e.g. "192.168.1.100"
private static final String DB_URL =
    "jdbc:mysql://" + DB_HOST + ":3306/chat_app"
    + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

private static final String DB_USER = "chatapp";
private static final String DB_PASSWORD = "chatapp123";
This class:

Loads the MySQL driver (com.mysql.cj.jdbc.Driver)

Tests the connection

Registers new users with BCrypt-hashed passwords

Authenticates existing users and updates last_login

Building the Project (Terminal)
From the chat-project directory:

macOS / Linux
bash
javac -cp .:lib/mysql-connector-j-9.5.0.jar:lib/jbcrypt-0.4.jar \
  ChatServer.java ChatClient.java DatabaseManager.java
Windows (PowerShell / cmd)
text
javac -cp .;lib\mysql-connector-j-9.5.0.jar;lib\jbcrypt-0.4.jar ^
  ChatServer.java ChatClient.java DatabaseManager.java
This produces .class files for all three classes.

Running the Server
On the machine that will act as the chat server:

macOS / Linux
bash
java -cp .:lib/mysql-connector-j-9.5.0.jar:lib/jbcrypt-0.4.jar ChatServer
Windows
text
java -cp .;lib\mysql-connector-j-9.5.0.jar;lib\jbcrypt-0.4.jar ChatServer
The server:

Listens on port 12345

Accepts multiple clients

Manages users and group channels in memory

Make sure the server machine's firewall allows TCP port 12345 and all devices are on the same LAN/Wi‑Fi.

Running the Client
On any device with Java and network access to:

MySQL (for login/signup)

The chat server (TCP port 12345)

macOS / Linux
bash
java -cp .:lib/mysql-connector-j-9.5.0.jar:lib/jbcrypt-0.4.jar ChatClient
Windows
text
java -cp .;lib\mysql-connector-j-9.5.0.jar;lib\jbcrypt-0.4.jar ChatClient
Client flow
Database connection test

text
Testing database connection...
>>> Database is reachable.
Auth menu

text
==== Chat Application ====
1. Login
2. Sign Up
0. Exit
Select option:
Sign Up: choose username + password (min 6 chars)

Login: authenticate with existing credentials

Server connection

text
Enter server IP address (or press Enter for localhost):
Enter server port (or press Enter for 12345):
On server machine → press Enter for localhost

On other devices → type the server's LAN IP, e.g. 192.168.1.100

Main menu

text
==== MAIN MENU ====
1. 1-to-1 chat
2. Group chat (channels)
0. Exit
Chat Features
1-to-1 Chat
Lists all online users except you.

Choose a user by number.

Options:

Send message.

Send file from your user folder.

Messages appear as:

text
[Alice]: Hi
[Bob]: Hello!
Group Chat Channels
Predefined channels: GroupA, GroupB, GroupC.

After joining a group:

See members in a neat numbered list.

Send messages to the whole group.

Send files to the group.

File Transfer
For 1-to-1 and group chat.

Files transferred as line-based text over the existing socket.

Received files stored inside a folder named after your username, prefixed with received_.

.gitignore Suggestions
Create a .gitignore to avoid committing build artifacts and user data:

text
*.class
*.jar
.DS_Store
.vscode/
user-folders/
*/
!lib/
*/received_*
Adjust paths to match how you create user folders.

Example Usage (Single Machine)
Start MySQL and set up DB/user.

Start ChatServer.

Open another terminal, run ChatClient.

Sign up Alice → login.

In a third terminal, run ChatClient again.

Sign up Bob → login.

Use the menus to start 1‑to‑1 chat or join GroupA.

Notes & Limitations
Authentication is handled on the client side via the shared MySQL DB; the chat server trusts whatever username it receives as the first message.

This is a console app intended for learning and demo purposes, not production.

File transfer assumes text files (line-based); binary files are not yet supported.

No TLS/SSL is used on the chat socket; use only on trusted networks.

Future Improvements
Server-side verification of auth tokens instead of trusting client usernames.

Support for binary file transfer.

Simple GUI client (JavaFX / Swing).

Configuration file for DB/port/IP.

Docker compose for easy setup (MySQL + server).