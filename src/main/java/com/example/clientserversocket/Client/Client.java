package com.example.clientserversocket.Client;// Importações necessárias para a funcionalidade do aplicativo,
// incluindo componentes da interface do usuário JavaFX, entrada/saída (I/O) e rede.
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

// Declaração da classe ClientFX, que permite criar um aplicativo JavaFX.
public class Client extends Application {
    // Constantes para o endereço e a porta do servidor.
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8080;

    // Componentes da interface do usuário e lista observável.
    private ListView<String> fileList = new ListView<>();
    private ObservableList<String> observableList = FXCollections.observableArrayList();

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Configurações iniciais da janela do aplicativo.
        primaryStage.setTitle("Cliente de Arquivos");

        final int iconSize = 15;

        // Criação e configuração do botão de upload.
        Button uploadButton = new Button("Enviar Arquivo");
        ImageView uploadIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/upload.png")));
        uploadIcon.setFitHeight(iconSize);
        uploadIcon.setFitWidth(iconSize);
        uploadButton.setGraphic(uploadIcon);

        // Ação para o botão de upload: selecionar um arquivo e fazer o upload.
        uploadButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                enviarArquivo(file);
            }
        });

        // Criação e configuração do botão de download.
        Button downloadButton = new Button("Baixar Arquivo Selecionado");
        ImageView downloadIcon = new ImageView(new Image(getClass().getResourceAsStream("/icons/download.png")));
        downloadIcon.setFitHeight(iconSize);
        downloadIcon.setFitWidth(iconSize);
        downloadButton.setGraphic(downloadIcon);

        // Ação para o botão de download: baixar o arquivo selecionado.
        downloadButton.setOnAction(event -> {
            String selectedFile = fileList.getSelectionModel().getSelectedItem();
            if (selectedFile != null) {
                baixarArquivo(selectedFile);
            }
        });

        // Configuração inicial da interface do usuário.
        fileList.setItems(observableList);
        VBox layout = new VBox(10, fileList, uploadButton, downloadButton);
        Scene scene = new Scene(layout, 400, 500);
        primaryStage.setScene(scene);
        primaryStage.show();
        scene.getStylesheets().add(getClass().getResource("/styles/style.css").toExternalForm());

        // Atualiza a lista de arquivos disponíveis.
        atualizarListaArquivos();
    }

    // Método para atualizar a lista de arquivos.
    private void atualizarListaArquivos() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    out.writeUTF("LIST");
                    int fileCount = in.readInt();
                    Platform.runLater(() -> observableList.clear());
                    for (int i = 0; i < fileCount; i++) {
                        String fileName = in.readUTF();
                        Platform.runLater(() -> observableList.add(fileName));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    // Método para enviar um arquivo para o servidor.
    private void enviarArquivo(File file) {
        new Thread(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 FileInputStream fis = new FileInputStream(file)) {
                out.writeUTF("UPLOAD");
                out.writeUTF(file.getName());
                out.writeLong(file.length());

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                DataInputStream in = new DataInputStream(socket.getInputStream());
                String response = in.readUTF();
                Platform.runLater(() -> exibirAlerta("Resposta do Servidor", response));
                atualizarListaArquivos();

            } catch (IOException e) {
                Platform.runLater(() -> exibirAlerta("Erro", "Falha ao enviar o arquivo."));
                e.printStackTrace();
            }
        }).start();
    }

    // Método para baixar um arquivo do servidor.
    private void baixarArquivo(String fileName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salvar Arquivo Baixado");
        fileChooser.setInitialFileName(fileName);
        File fileToSave = fileChooser.showSaveDialog(null);

        if (fileToSave == null) {
            exibirAlerta("Download Cancelado", "Nenhum local selecionado para salvar o arquivo.");
            return;
        }

        Task<Void> downloadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    out.writeUTF("DOWNLOAD");
                    out.writeUTF(fileName);

                    String serverResponse = in.readUTF();
                    if ("OK".equals(serverResponse)) {
                        long fileSize = in.readLong();
                        try (FileOutputStream fos = new FileOutputStream(fileToSave)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while (fileSize > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                                fos.write(buffer, 0, bytesRead);
                                fileSize -= bytesRead;
                            }
                        }
                        Platform.runLater(() -> exibirAlerta("Sucesso", "Arquivo baixado com sucesso."));
                    } else {
                        throw new IOException("Resposta do servidor: " + serverResponse);
                    }
                } catch (IOException e) {
                    Platform.runLater(() -> exibirAlerta("Erro", "Falha ao baixar o arquivo: " + e.getMessage()));
                    throw e;
                }
                return null;
            }
        };

        new Thread(downloadTask).start();
    }

    // Método para exibir alertas na interface do usuário.
    private void exibirAlerta(String titulo, String mensagem) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(titulo);
            alert.setHeaderText(null);
            alert.setContentText(mensagem);
            alert.showAndWait();
        });
    }

    // Ponto de entrada do aplicativo.
    public static void main(String[] args) {
        launch(args);
    }
}