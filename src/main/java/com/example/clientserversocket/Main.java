package com.example.clientserversocket;


public class Main {
    public static void main(String[] args) {
        // Inicia o servidor em uma nova thread para não bloquear a execução do cliente.
        new Thread(() -> {
            try {
                com.example.clientserversocket.Server.Server.main(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Aguarda um pouco para garantir que o servidor esteja pronto para aceitar conexões.
        try {
            Thread.sleep(2000); // Aguarda 2 segundos
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Inicia o cliente.
        // Note que essa chamada bloqueará a thread atual, então é o último comando.
        try {
            com.example.clientserversocket.Client.Client.main(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}