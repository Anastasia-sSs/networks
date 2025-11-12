package client;


import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.lang.System.exit;

public class Client {
    public static final int MAX_SIZE_FILE_NAME = 4096;
    public static final int SIZE_BUFFER = 8192;//определить лучший размер позже
    public static final long MAX_SIZE_FILE = 1024L * 1024 * 1024 * 1024;

    public static String fileName;
    public static byte[] fileNameByte;
    public static long fileSize;
    public static InetAddress serverInetAddress;
    public static int serverPort;
    public static Path pathToFile;

    public static void main(String[] args) {
        CheckArgs(args);
        try (Socket socket = new Socket()){
            socket.connect(new InetSocketAddress(serverInetAddress, serverPort), 10000);
            sendFile(socket);
            getResponse(socket);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void CheckArgs(String[] args) {
        if (args.length != 3) {
            System.err.println("File name, ip and port are not specified");
            exit(1);
        }

        try {
            pathToFile = Path.of(args[0]);
            serverInetAddress =  InetAddress.getByName(args[1]);
            serverPort = Integer.parseInt(args[2]);

            if (!Files.exists(pathToFile) || !Files.isRegularFile(pathToFile)) {
                System.err.println("File does not exist or is not regular");
                exit(1);
            }
            fileSize = Files.size(pathToFile);
            if (fileSize > MAX_SIZE_FILE) {
                System.err.println("File is large (no more then 1 Tb is allowed):" + fileSize);
                exit(1);
            }
            fileName = pathToFile.getFileName().toString();
            fileNameByte = fileName.getBytes(StandardCharsets.UTF_8);
            if (fileNameByte.length > MAX_SIZE_FILE_NAME) {
                System.err.println("File name (UTF-8 format) is too long: " + fileNameByte.length);
                exit(1);
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Invalid port: " + args[2] + "\n" + e.getMessage());
        }

    }

    public static void sendFile(Socket socket) throws IOException{
        DataOutputStream out =  new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.writeShort(fileNameByte.length);
        out.write(fileNameByte);
        out.writeLong(fileSize);

        byte[] arrByteBuffer = new byte[SIZE_BUFFER];
        BufferedInputStream clientFile = new BufferedInputStream(Files.newInputStream(pathToFile), SIZE_BUFFER);
        int readedBytes;
        while ((readedBytes = clientFile.read(arrByteBuffer, 0, SIZE_BUFFER)) != -1) {
            out.write(arrByteBuffer, 0, readedBytes);
        }
        out.flush();
    }

    public static void getResponse(Socket socket) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String message = bufferedReader.readLine();
        System.err.println("Server: " + message);
    }

}