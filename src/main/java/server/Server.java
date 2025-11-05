package server;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.lang.System.exit;

public class Server {
    public static final int SIZE_FILE_NAME = 4096;
    public static final int SIZE_FILE_SIZE = 5;
    public static final int SIZE_BUFFER = 8192; //определить лучший размер позже


    public static String fileName;
    public static Integer expectedFileSize;
    public static File clientsDirectory;

    public static Socket clientSocket;
    public static BufferedInputStream in;
    public static BufferedOutputStream out;


    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("где параметр (порт сервера, на котором он будет слушать)?");
            exit(1);
        }
        int portListen = Integer.parseInt(args[0]);

        try (ServerSocket serverSocket = new ServerSocket(portListen)){
            clientSocket = serverSocket.accept();
            createDirectory();

            //непонятно, что лучше DataInputStream или BufferedInputStream
            in = new BufferedInputStream(clientSocket.getInputStream()); //от клиента к серверу (чтение)
            out = new BufferedOutputStream(clientSocket.getOutputStream());
            getInfoFileClient(in); //rename later
            int readingByte = readFile(in);
            responseClient(readingByte, out);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
           shutdown();
        }

    }

    public static void getInfoFileClient(BufferedInputStream in) throws IOException {
        byte[] arrByteFile = new byte[SIZE_FILE_NAME + SIZE_FILE_SIZE];
        int off = 0;
        int total = 0;
        int maxLen = arrByteFile.length;
        int readingByte;
        while ((readingByte = in.read(arrByteFile, off, maxLen)) != -1) {
            if ((total += readingByte) == maxLen) {
                break;
            }
            off += total;
            maxLen -= total;
        }
        if (readingByte == -1) {
            throw new IOException("socker closed, when trying to read the head");
        }
        fileName = new String(Arrays.copyOfRange(arrByteFile, 0, SIZE_FILE_NAME), StandardCharsets.UTF_8);
        expectedFileSize = Integer.valueOf(new String(Arrays.copyOfRange(arrByteFile, SIZE_FILE_NAME,
                SIZE_FILE_NAME + SIZE_FILE_SIZE)));
    }

    public static int readFile(BufferedInputStream in) throws IOException {
        File file = new File("uploads", fileName); //здесь нужна проверка на единственность существования файла
        BufferedOutputStream clientFile = new BufferedOutputStream(new FileOutputStream(file, true));

        byte[] arrByteFile = new byte[SIZE_BUFFER];
        int readingByte;
        while ((readingByte = in.read(arrByteFile)) != -1) {
            clientFile.write(arrByteFile); //так, запись идет с начала (то есть есть ли затирание?)
            readingByte ++;                //запись массива идет с мусором? (ну не все же 8 кб будут заполнены)
        }
        clientFile.close();
        return readingByte;
    }

    public static void createDirectory() throws Exception{
        clientsDirectory = new File("uploads");
        if (!clientsDirectory.mkdir()) {
            throw new Exception("directory don't created");
        }
    }

    public static void responseClient(int readingByte, BufferedOutputStream out) throws IOException {
        String message;
        if (readingByte == expectedFileSize) {
            message = "OK: все прочитано\n";
        } else {
            message = "OH, SILLY: где-то байтики не дошли...\n";
        }
        out.write(message.getBytes());
    }

    public static void shutdown() throws IOException {
        if (!clientSocket.isClosed()) {
            clientSocket.close();
        }
        in.close(); //а что если еще не успели выделить ресурсы на это?
        out.close();
    }
}