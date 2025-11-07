package server;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.lang.System.exit;

public class Server {
    public static final int SIZE_BUFFER = 8192; //определить лучший размер позже
    public static final int SIZE_HEADER_LENGTH= 2;


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
        byte[] arrSizeHeader = new byte[SIZE_HEADER_LENGTH];
        ByteBuffer byteBuffer = ByteBuffer.allocate(SIZE_HEADER_LENGTH);
        int lengthHeader;
        if (in.read(arrSizeHeader) == -1) {
            throw new IOException("опачки, что-то не читается файл");
        }
        byteBuffer.put(arrSizeHeader);
        lengthHeader = byteBuffer.getInt(0);

        int readedBytes = 0;
        byte[] arrHeader = new byte[lengthHeader];
        int offset = 0;
        int length = 0;
        while ((readedBytes = in.read(arrHeader, offset, length)) != lengthHeader) {
            offset += readedBytes;
            length -= readedBytes;
            if (readedBytes == -1) {
                throw new IOException("опачки, что-то не читается файл");
            }
        }
        String stringInfoFileClient = new String(arrHeader, StandardCharsets.UTF_8);
        String[] arrInfoFileClient = stringInfoFileClient.split("#", -1);
        if (arrInfoFileClient.length != 3) {
            throw new IOException("мда... треш");
        }
        fileName = arrInfoFileClient[0];
        expectedFileSize = Integer.valueOf(arrInfoFileClient[1]);
    }

    public static int readFile(BufferedInputStream in) throws IOException {
        File file = new File("uploads", fileName); //здесь нужна проверка на единственность существования файла
        BufferedOutputStream clientFile = new BufferedOutputStream(new FileOutputStream(file, true));

        byte[] arrByteFile = new byte[SIZE_BUFFER];
        int readingByte;
        int total = 0;
        while ((readingByte = in.read(arrByteFile)) != -1) {
            clientFile.write(arrByteFile); //так, запись идет с начала (то есть есть ли затирание?)
            total += readingByte;                //запись массива идет с мусором? (ну не все же 8 кб будут заполнены)
        }
        clientFile.close();
        return total;
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