/*
AdministradorTrafico.java
Author: Miguel Gomez
Data Created: 11 April 2026
Description
Manage the traffic and reply data in the both data base of machines
Usage
Manage traffic
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;


public class AdministradorTrafico {
    static String principal_host;
    static int principal_port;
    static String second_host;
    static int second_port;
    static int local_port;



    public static void main(String[] args) throws  Exception{
        if (args.length != 5){
            System.err.println("Uso: sudo java AdministradorTrafico <host-principal> <puerto-principal> <ip-replica> <puerto-replica> <puerto-local>");
            System.exit(1);
        }
        principal_host = args[0];
        principal_port = Integer.parseInt(args[1]);
        second_host = args[2];
        second_port= Integer.parseInt(args[3]);
        local_port = Integer.parseInt(args[4]);


        //  Inicio del servidor en el puerto 80
        ServerSocket server = new ServerSocket(local_port);
        for (;;){
            Socket cliente = server.accept();
            new WorkerClonador(cliente).start();

        }

    }

    // Logica para la clonación
    static class WorkerClonador extends Thread  {
        Socket client, principalServer, secondServer;
        WorkerClonador(Socket client){
            this.client = client;
        }

        @Override
        public void run() {
            try{
                principalServer = new Socket(principal_host, principal_port);
                secondServer = new Socket(second_host, second_port);

                // Connection
                new BridgeClone(client.getInputStream(), principalServer.getOutputStream(), secondServer.getOutputStream()).start();

                // Response
                new BridgeHome(principalServer.getInputStream(), client.getOutputStream()).start();
                // Trash
                new BridgeTrash(secondServer.getInputStream()).start();

            } catch ( IOException e){
            System.err.println("Erro connection" + e.getMessage());
            }

        }
    }

    static class BridgeClone extends Thread {
    }

    static class BridgeHome extends Thread{

    }

    static class BridgeTrash extends Thread{

    }


    }

