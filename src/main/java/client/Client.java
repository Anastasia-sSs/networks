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
    public static final double MAX_SIZE_FILE = Math.pow(2, 40);

    public static String fileName;
    public static int fileNameByteLength;
    public static long fileSize;
    public static InetAddress serverInetAddress;
    public static int serverPort;
    public static Path pathToFile;

    public static void main(String[] args) {
        CheckArgs(args);
        try (Socket socket = new Socket()){
            socket.connect(new InetSocketAddress(serverInetAddress, serverPort), 0);
            sendFile(socket);
            getResponse(socket);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }

    public static void CheckArgs(String[] args) {
        if (args.length != 3) {
            System.err.println("File name, ip and port are not specified");
            exit(1);
        }

        try {
            pathToFile = Path.of(args[0]);
            serverInetAddress =  InetAddress.getByAddress(args[1].getBytes());
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
            byte[] fileNameByte = fileName.getBytes(StandardCharsets.UTF_8);
            fileNameByteLength = fileNameByte.length;
            if (fileNameByteLength > MAX_SIZE_FILE_NAME) {
                System.err.println("File name (UTF-8 format) is too long: " + fileNameByteLength);
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
        out.writeShort(fileNameByteLength);
        out.writeUTF(fileName);
        out.writeLong(fileSize);

        byte[] arrByteBuffer = new byte[SIZE_BUFFER];
        BufferedInputStream clientFile = new BufferedInputStream(Files.newInputStream(pathToFile), SIZE_BUFFER);
        int readedBytes;
        while ((readedBytes = clientFile.read(arrByteBuffer, 0, SIZE_BUFFER)) != -1) {
            out.write(arrByteBuffer, 0, readedBytes);
        }
        out.flush();
        clientFile.close();
    }

    public static void getResponse(Socket socket) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String message = bufferedReader.readLine();
        System.out.println("Server: " + message);
    }

}