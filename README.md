# Concurrent File Copy Java Application

This is a Java project for a concurrent file copy application developed as part of the course "Konkurentné programovanie". The application allows you to copy directories in a client-server architecture, emphasizing parallelism and resilience to interruptions. The program provides a graphical user interface for the client using JavaFX and uses multithreading and socket-based communication to efficiently copy files.

## Table of Contents

- [About the Project](#about-the-project)
- [Features](#features)
- [Usage](#usage)
- [Code Principle](#code-principle)

## About the Project

The main goal of this project is to develop a client-server application for copying directories efficiently. The key features of the application include:

- Parallel copying using a user-defined number of TCP sockets.
- Resilience to interruptions such as server or client shutdown, allowing for seamless resumption of copying.
- Use of Java's Executor framework for managing threads and at least one synchronizer.
- A graphical user interface for the client with progress bars, thread configuration, and copy control.

## Features

- Efficient parallel copying of directories using multiple TCP sockets.
- Resumption of copying after interruptions (server or client shutdown).
- Thread management using the Executor framework.
- Synchronizer for thread synchronization.
- JavaFX-based graphical user interface for the client.
- Progress bars to visualize copying progress in terms of files and data size.
- User-configurable thread count for copying.
- Start and resume buttons for controlling the copying process.

## Usage

1. **Start the Server:**

   Begin by launching the server component, specifying the directory to be provided for copying in `config.properties`.

2. **Launch the Client Application:**

   Start the client application, which will open a graphical user interface.

3. **Configure the Number of Threads:**

   Optionally, configure the number of threads to be used for copying. You can adjust this setting to optimize the copying process.

4. **Initiate the Copy Process:**

   Click the "Start Copy" button to initiate the copying process. The application will efficiently copy files using parallel threads and TCP sockets.

5. **Track Progress:**

   The graphical user interface provides progress bars to visualize the copying progress in terms of both the number of files and the data size being copied.

## Code Principle

### Server

- The `Server` class represents the server-side of the application and is responsible for serving files to clients.

- It performs the following functions:

  - Searches for files in a specified directory, counting the total number of files and their combined size and saving it to `BlockingQueue` of `FileInfo`.
  - Handles connections from multiple clients using sockets.
  - Sends `BlockingQueue` to server, that represents files to send.
  - Runs a `Callable` task `FileSendTask` to listen for client's requests `FileInfo` and then efficiently reads and sends files to clients in chunks.

- In case of interrupting connection or `Client`'s termination progress data is saved and then loaded to enable resumption of interrupted transfers.

### Client

- The `Client` class represents the client-side of the application and is responsible for initiating and managing the copying process.

- It communicates with the server and performs the following tasks:

  - Establishes a connection with the server using sockets.
  - Determines the number of threads to use for parallel copying.
  - Receives a `BlockingQueue` about files from the server. The queue contains `FileInfo` objects representing files, their sizes, and the offsets from which they should be copied.
  - Creates directories and files on the client side with properly structure.
  - Initiates the copying of files using parallel threads and TCP sockets. Runs client task (`FileReceiveTask`) that requests files from the server, starting from a specific offset, and saves them on the client side.

- In case of interrupting connection or `Server`'s termination progress data is saved and then loaded to enable resumption of interrupted transfers.

### Shared Components

- Both the client and server use the `FileInfo` class to represent file information, such as filename, offset, and size.

- The `PropertiesManager` class is used to manage application properties and configuration.

- The `FilePathChanger` class handles file path modifications based on the operating system.

