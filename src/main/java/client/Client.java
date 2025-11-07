package client;


import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static java.lang.System.exit;

public class Client {
    public static final int SIZE_FILE_NAME = 4096;
    public static final int SIZE_HEADER_LENGTH = 2;
    public static final int SIZE_FILE_SIZE = 6;
    public static final int SIZE_BUFFER = 8192;//определить лучший размер позже
    public static final double MAX_SIZE_FILE = Math.pow(2, 40);
    public static File file;


    public static String fileName;
    public static Integer expectedFileSize;

    public static BufferedInputStream in;
    public static BufferedOutputStream out;


    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("где параметры (путь к файлу, ip и номер порта сервера)?");
            exit(1);
        }
        fileName = new String(args[0].getBytes(), StandardCharsets.UTF_8);
        if (fileName.getBytes().length > 2096) {
            System.out.println("другой файл давай, у этого длина слишком большая!!!");
            exit(0);
        }
        file = new File(fileName);
        if (!file.exists() || (file.length() > MAX_SIZE_FILE)) {
            System.out.println("такого файла нет или что-то ты переборщил с размером. попробуй, дружочек, еще раз))");
            exit(1);
        }
        String ipAddressServer = args[1];
        int portServer = Integer.parseInt(args[0]);

        try (Socket socket = new Socket(InetAddress.getByName(ipAddressServer), portServer)){
            //непонятно, что лучше DataInputStream или BufferedInputStream

            sendFile(socket);

            getResponseServer(socket);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            shutdown();
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