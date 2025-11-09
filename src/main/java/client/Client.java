package client;


import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.System.exit;
import static java.lang.foreign.MemorySegment.NULL;

public class Client {
    public static final int MAX_SIZE_FILE_NAME = 4096;
    public static final int SIZE_HEADER_LENGTH = 2;
    public static final int SIZE_FILE_SIZE = 6;
    public static final int SIZE_BUFFER = 8192;//определить лучший размер позже
    public static final double MAX_SIZE_FILE = Math.pow(2, 40);
    public static File file;

    public static String fileName;
    public static InetAddress serverInetAddress;
    public static int serverPort;

    public static Integer expectedFileSize;

    public static BufferedInputStream in;
    public static BufferedOutputStream out;


    public static void main(String[] args) throws IOException {
        CheckArgs(args);

        try (Socket socket = new Socket()){
            socket.connect(new InetSocketAddress(serverInetAddress, serverPort), 0);

            sendFile(socket);

            getResponseServer(socket);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            shutdown();
        }

    }

    public static void CheckArgs(String[] args) {
        if (args.length != 3) {
            System.err.println("File name, ip and port are not specified");
            exit(1);
        }

        Path path = Path.of(args[0]);
        try {
            serverInetAddress =  InetAddress.getByAddress(args[1].getBytes());
            serverPort = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port: " + args[2] + "\n" + e.getMessage());
        } catch (UnknownHostException e) {
            System.err.println("Invalid ip:" + args[1] + "\n" + e.getMessage());
        }

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            System.err.println("File does not exist or is not regular");
            exit(1);
        }
        try {
            long fileSize = Files.size(path);
            if (fileSize > MAX_SIZE_FILE) {
                System.err.println("File is large (no more then 1 Tb is allowed):" + fileSize);
                exit(1);
            }
            fileName = path.getFileName().toString();
            byte[] fileNameByte = fileName.getBytes(StandardCharsets.UTF_8);
            int fileNameByteLength = fileNameByte.length;
            if (fileNameByteLength > MAX_SIZE_FILE_NAME) {
                System.err.println("File name (UTF-8 format) is too long: " + fileNameByteLength);
                exit(1);
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

    }

    public static void setInfoFileClient(BufferedOutputStream out) throws IOException {
        ByteArrayOutputStream firstByteArray = new ByteArrayOutputStream(SIZE_HEADER_LENGTH);
        ByteArrayOutputStream secondByteArray = new ByteArrayOutputStream();

        secondByteArray.write(fileName.getBytes());
        secondByteArray.write("#".getBytes());
        secondByteArray.write(Long.valueOf(fileName.length()).toString().getBytes());
        secondByteArray.write("#".getBytes());
        firstByteArray.write(secondByteArray.size());

        out.write(firstByteArray.toByteArray());
        out.write(secondByteArray.toByteArray());
    }

    public static void sendFile(Socket socket) {
        try {
            out = new BufferedOutputStream(socket.getOutputStream()); //от клиента к сереру (запись)
            setInfoFileClient(out);

            byte[] arrByteBuffer = new byte[SIZE_BUFFER];
            BufferedInputStream clientFile = new BufferedInputStream(new FileInputStream(file)); //читаем с файла
            while ((clientFile.read(arrByteBuffer) != SIZE_BUFFER) || ((clientFile.read(arrByteBuffer) != -1))) {
                out.write(arrByteBuffer);
            }


        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

    }

    public static void getResponseServer(Socket socket) throws IOException {
        in = new BufferedInputStream(socket.getInputStream());
        byte[] buffer = new byte[50]; //потом убрать маг число и инищиализировать, обосновав, почему именно такое
        while (in.read(buffer) != -1) {
            String message = new String(buffer);
            System.out.println(message);
        }
    }

    public static void shutdown() throws IOException {
        in.close(); //а что если еще не успели выделить ресурсы на это?
        out.close();
    }
}