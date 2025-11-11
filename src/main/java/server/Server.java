package server;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.System.exit;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

public class Server {
    public static final int SIZE_BUFFER = 8192; //определить лучший размер позже

    public static Path UPLOADS_DIR = Path.of("uploads");
    public static int listeningPort;
    public static ExecutorService clientThreadPool = Executors.newCachedThreadPool();


    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Port is not specified");
            exit(1);
        }
        listeningPort = Integer.parseInt(args[0]);
        start();

    }

    public static void start() {
        try (ServerSocket serverSocket = new ServerSocket(listeningPort)){
            if (!Files.exists(UPLOADS_DIR)) {Files.createDirectory(UPLOADS_DIR);}
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                clientThreadPool.submit(new Connection(clientSocket));
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static class Connection implements Runnable{
        private final Socket socket;
        private Instant startTime;
        private String clientSocketString;
        private long fileSize;

        private final AtomicLong totalReadBytes = new AtomicLong(0);
        private final AtomicLong penultReadBytes = new AtomicLong(0);
        private final double SECONDS = 3.0;

        Connection(Socket socket) {
            this.socket = socket;
        }


        public void run() {
            clientSocketString = socket.getLocalSocketAddress().toString();
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
            startTime = Instant.now();

            Runnable Task = () -> {
                long lastTotal = totalReadBytes.get();
                long total = penultReadBytes.getAndSet(lastTotal);
                double instantSpeed = (lastTotal - total) / SECONDS;
                double avgSpeed = lastTotal / (Duration.between(startTime, Instant.now()).toMillis() / 1000.0);
                System.out.printf("%s: instant speed: %.3f byte/s, average speed: %.3f byte/s \n",
                        clientSocketString, instantSpeed, avgSpeed);
            };
            executorService.scheduleAtFixedRate(Task, 3, 3, TimeUnit.SECONDS);

            readFile();
            executorService.shutdownNow();
            sendResponse();
        }

        public void sendResponse() {
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                long receivedBytes = totalReadBytes.get();
                if (receivedBytes == fileSize) {
                    out.println("Server: OK!!! I received " + receivedBytes + "bytes");
                } else {
                    out.println("Server: ERROR!!! I received " + receivedBytes + "bytes and file size is" + fileSize);
                }
                out.flush();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }

        //что с названиями переменных...
        public Path createUniqueFileName(String pathString) throws IOException{
            Path path = UPLOADS_DIR.resolve(pathString);
            if (!Files.exists(path)) {
                return path;
            }

            String format = "";
            for (int i = 0; i < Integer.MAX_VALUE; i++) {
                int dotIndex = pathString.lastIndexOf(".");
                if (dotIndex > 0) {
                     format = pathString.substring(dotIndex);
                     pathString = pathString.substring(0, dotIndex);
                }
                path = UPLOADS_DIR.resolve(pathString + "_" + i + "." + format);
                if (!Files.exists(path)) {
                    return path;
                }
            }
            throw new IOException("File don't create");
        }

        public void readFile () {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
                short sizeFileName = in.readShort();
                byte[] fileNameByte = new byte[sizeFileName];
                in.readFully(fileNameByte);
                fileSize = in.readLong();

                String fileName = new String(fileNameByte, StandardCharsets.UTF_8);
                Path path = createUniqueFileName(fileName);

                OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(path, CREATE_NEW), SIZE_BUFFER);
                byte[] buffer = new byte[SIZE_BUFFER];
                long remainder = fileSize;
                int readBytes;
                while (remainder > 0) {
                    if (remainder < SIZE_BUFFER) {
                        readBytes = in.read(buffer, 0, (int)remainder);
                    } else {
                        readBytes = in.read(buffer, 0, SIZE_BUFFER);
                    }
                    if (readBytes == -1) {break;}
                    fileOut.write(buffer, 0, readBytes);
                    totalReadBytes.addAndGet(readBytes);
                    remainder -= readBytes;
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            } finally { //после разобраться
                long lastTotal = totalReadBytes.get();
                long total = penultReadBytes.getAndSet(lastTotal);
                double instantSpeed = (lastTotal - total) / SECONDS;
                double avgSpeed = lastTotal / (Duration.between(startTime, Instant.now()).toMillis() / 1000.0);
                System.out.printf("%s: instant speed: %.3f byte/s, average speed: %.3f byte/s \n",
                        clientSocketString, instantSpeed, avgSpeed);
            }
        }

    }
}