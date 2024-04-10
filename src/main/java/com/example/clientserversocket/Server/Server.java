package com.example.clientserversocket.Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final int PORT = 8080;
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "admin";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor ouvindo na porta " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Exceção no servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                String command = in.readUTF();

                switch (command.toUpperCase()) {
                    case "LIST":
                        listarArquivos(out);
                        break;
                    case "UPLOAD":
                        enviarArquivo(in, out);
                        break;
                    case "DOWNLOAD":
                        baixarArquivo(in, out);
                        break;
                    case "DELETE":
                        break;
                    default:
                        out.writeUTF("Comando inválido.");
                        break;
                }
            } catch (IOException e) {
                System.out.println("Exceção no servidor: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void listarArquivos(DataOutputStream out) throws IOException {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM files")) {

                List<String> files = new ArrayList<>();
                while (rs.next()) {
                    files.add(rs.getString("name"));
                }

                out.writeInt(files.size());
                for (String file : files) {
                    out.writeUTF(file);
                }
            } catch (SQLException e) {
                out.writeInt(0);
                System.out.println("Erro de conexão com o banco de dados: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void enviarArquivo(DataInputStream dis, DataOutputStream dos) throws IOException {
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();
            File directory = new File("src/server_storage/");
            if (!directory.exists()) directory.mkdir();
            File file = new File(directory, fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long remaining = fileSize;
                while (remaining > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
            } catch (IOException e) {
                System.out.println("Falha ao salvar o arquivo: " + e.getMessage());
                dos.writeUTF("Falha ao enviar o arquivo.");
                return;
            }

            // Inserção no banco de dados
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                conn.setAutoCommit(false);
                String sql = "INSERT INTO files (name, path, size) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, fileName);
                    pstmt.setString(2, file.getAbsolutePath());
                    pstmt.setLong(3, file.length());
                    int affectedRows = pstmt.executeUpdate();
                    if (affectedRows == 1) {
                        conn.commit();
                        dos.writeUTF("Upload concluído com sucesso.");
                    } else {
                        dos.writeUTF("Arquivo enviado, mas falha ao registrar no banco de dados.");
                    }
                }
            } catch (SQLException e) {
                System.out.println("Erro de SQL: " + e.getMessage());
                dos.writeUTF("Arquivo enviado, mas falha ao registrar no banco de dados.");
            }
        }

        private void baixarArquivo(DataInputStream dis, DataOutputStream dos) throws IOException {
            String fileName = dis.readUTF();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "SELECT path FROM files WHERE name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, fileName);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        String filePath = rs.getString("path");
                        File file = new File(filePath);
                        if (file.exists()) {
                            dos.writeUTF("OK");
                            dos.writeLong(file.length());
                            try (FileInputStream fis = new FileInputStream(file)) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = fis.read(buffer)) != -1) {
                                    dos.write(buffer, 0, bytesRead);
                                }
                            }
                        } else {
                            dos.writeUTF("Erro: Arquivo não encontrado no servidor.");
                        }
                    } else {
                        dos.writeUTF("Erro: Arquivo não registrado no banco de dados.");
                    }
                }
            } catch (SQLException e) {
                System.out.println("Erro no banco de dados: " + e.getMessage());
                dos.writeUTF("Erro no banco de dados.");
            }
        }
    }
}
