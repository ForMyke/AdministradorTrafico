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
    static String host_uno;
    static int puerto_uno;
    static String host_dos;
    static int puerto_dos;
    static int puerto_local;



    public static void main(String[] args) throws  Exception{
        if (args.length != 5){
            System.err.println("Uso: sudo java AdministradorTrafico <host-principal> <puerto-principal> <ip-replica> <puerto-replica> <puerto-local>");
            System.exit(1);
        }
        host_uno = args[0];
        puerto_uno = Integer.parseInt(args[1]);
        host_dos = args[2];
        puerto_dos= Integer.parseInt(args[3]);
        puerto_local = Integer.parseInt(args[4]);


        //  Inicio del servidor en el puerto 80
        ServerSocket server = new ServerSocket(puerto_local);
        for (;;){
            Socket cliente = server.accept();
            new WorkerClonador(cliente).start();

        }

    }

    // Logica para la clonación
    static class WorkerClonador extends Thread  {

    }

    static class thread_uno extends Thread {
    }

    static class thread_dos extends Thread{

    }

    static class thread_tres extends Thread{

    }


    }

