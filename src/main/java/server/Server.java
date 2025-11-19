package server;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

import static java.lang.System.exit;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

public class Server {
    public static final int SIZE_BUFFER = 8192;
    public static final int MAX_FILENAME_BYTES = 4096;
    public static final long MAX_FILE_SIZE = 1024L * 1024 * 1024 * 1024;

    public static Path UPLOADS_DIR = Path.of("uploads");
    public static int listeningPort;
    public static ExecutorService clientThreadPool = Executors.newCachedThreadPool();

    private static final ScheduledExecutorService statScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final ConcurrentMap<String, StatClient> statMap = new ConcurrentHashMap<>();


    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Port is not specified");
            exit(1);
        }
        listeningPort = Integer.parseInt(args[0]);
        start();

    }

    public static void start() {
        try (ServerSocket serverSocket = new ServerSocket(listeningPort)) {
            if (!Files.exists(UPLOADS_DIR)) {Files.createDirectory(UPLOADS_DIR);}

            statScheduler.scheduleAtFixedRate(() -> {
                for (Map.Entry<String, StatClient> entry : statMap.entrySet()) {
                    StatClient stat = entry.getValue();
                    long total = stat.totalBytes;
                    long prev = stat.lastReportedBytes;
                    stat.lastReportedBytes = total;
                    double instantSpeed = (total - prev) / 3.0;
                    double avgSpeed = total / Math.max(0.001, (Duration.between(stat.startTime, Instant.now()).toMillis() / 1000.0));
                    System.out.printf("%s: Instant Speed: %.3f B/s, Average Speed: %.3f B/s \n",
                            stat.clientId, instantSpeed, avgSpeed);
                }
            }, 3, 3, TimeUnit.SECONDS);

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                clientThreadPool.submit(new Connection(clientSocket));
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            clientThreadPool.shutdownNow();
            statScheduler.shutdownNow();
        }

    }

    private static class StatClient {
        String clientId;
        Instant startTime;
        volatile long totalBytes = 0;
        volatile long lastReportedBytes = 0;

        StatClient(String clientId) {
            this.clientId = clientId;
            this.startTime = Instant.now();
        }
    }


    private static class Connection implements Runnable{
        private final Socket socket;
        private String clientId;
        private StatClient stat;
        private String fileName;
        private long fileSize;
        private Path filePath;
        private boolean allBytesReadFlag = false;

        Connection(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            clientId = socket.getRemoteSocketAddress().toString();
            stat = new StatClient(clientId);
            statMap.put(clientId, stat);
            try {
                handleClient();
                socket.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            } finally {
                report();
                cleanup();
                statMap.remove(clientId);
            }
        }

        public void handleClient() throws IOException {
            try (DataInputStream in = new DataInputStream((new BufferedInputStream(socket.getInputStream())))){
                readHeader(in);
                filePath = createUniqueFileName();

                receiveFile(in);
                allBytesReadFlag = (stat.totalBytes == fileSize);
                sendResponse();
            } catch (IOException e) {
                sendResponse();
            }
        }

        public void sendResponse() throws IOException{
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                if (allBytesReadFlag) {
                    out.println("Server: OK!!! I received " + stat.totalBytes + "bytes");
                } else {
                    out.println("Server: ERROR!!! I received " + stat.totalBytes + "bytes and file size is" + fileSize);
                }
                out.flush();
            }
        }

        private void readHeader(DataInputStream in) throws IOException {
            int nameLength = in.readUnsignedShort();
            if (nameLength == 0 || nameLength > MAX_FILENAME_BYTES) {
                throw new IOException("Invalid filename length: " + nameLength);
            }
            byte[] nameBytes = new byte[nameLength];
            in.readFully(nameBytes);
            fileName = new String(nameBytes, StandardCharsets.UTF_8);

            fileSize = in.readLong();
            if (fileSize < 0 || fileSize > MAX_FILE_SIZE) {
                throw new IOException("Invalid file size: " + fileSize);
            }

        }

        private Path createUniqueFileName() throws IOException {
            String nameFile = Path.of(fileName).getFileName().toString();
            Path path = UPLOADS_DIR.resolve(nameFile);
            if (!Files.exists(path)) {
                return path;
            }

            String base = nameFile;
            String format = "";
            int dot = nameFile.lastIndexOf('.');
            if (dot > 0) {
                base = nameFile.substring(0, dot);
                format = nameFile.substring(dot);
            }
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                path = UPLOADS_DIR.resolve(base + "_" + i + format);
                if (!Files.exists(path)) {
                    return path;
                }
            }
            throw new IOException("Can not generate unique filename for " + nameFile);
        }

        private void receiveFile(DataInputStream in) throws IOException {
            try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(filePath, CREATE_NEW), SIZE_BUFFER)) {
                byte[] buffer = new byte[SIZE_BUFFER];
                long remainder = fileSize;
                int readBytes;
                while (remainder > 0) {
                    if (remainder < SIZE_BUFFER) {
                        readBytes = in.read(buffer, 0, (int) remainder);
                    } else {
                        readBytes = in.read(buffer, 0, SIZE_BUFFER);
                    }
                    if (readBytes == -1) { break; }
                    fileOut.write(buffer, 0, readBytes);
                    stat.totalBytes += readBytes;
                    remainder -= readBytes;
                }
                fileOut.flush();
            }
        }

        private void report() {
            long total = stat.totalBytes;
            long prev = stat.lastReportedBytes;
            stat.lastReportedBytes = total;
            double instantSpeed = (total - prev) / 3.0;
            double avgSpeed = total / Math.max(0.001, (Duration.between(stat.startTime, Instant.now()).toMillis() / 1000.0));
            System.out.printf("%s: Instant Speed: %.3f B/s, Average Speed: %.3f B/s \n",
                    stat.clientId, instantSpeed, avgSpeed);
        }

        private void cleanup() {
            try {
                if (!allBytesReadFlag && filePath != null) {
                    Files.deleteIfExists(filePath);
                    System.out.println(clientId + ": Partial file deleted: " + filePath);
                }
            } catch(IOException e){
                System.err.println(clientId + ": Failed to delete partial file " + filePath);
                System.err.println(e.getMessage());
            }
        }
    }
}
