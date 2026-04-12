# AdministradorTrafico

Traffic manager that clones incoming requests to two database servers simultaneously, forwarding responses only from the primary server.

## Requirements

- Java 21

## Compilation

```bash
javac AdministradorTrafico.java
```

## Usage

```bash
sudo java AdministradorTrafico <host-principal> <puerto-principal> <ip-replica> <puerto-replica> <puerto-local>
```

### Arguments

| Argument | Description |
|---|---|
| `host-principal` | Primary server host |
| `puerto-principal` | Primary server port |
| `ip-replica` | Replica server host |
| `puerto-replica` | Replica server port |
| `puerto-local` | Local port to listen on |

### Example

```bash
sudo java AdministradorTrafico 192.168.1.10 3306 192.168.1.11 3306 80
```

## How it works

1. Listens for incoming client connections on `puerto-local`.
2. For each connection, forwards the request to both the primary and replica servers (`BridgeClone`).
3. Returns only the primary server's response to the client (`BridgeHome`).
4. Discards the replica server's response (`BridgeTrash`).

## Author

Miguel Gomez — April 11, 2026