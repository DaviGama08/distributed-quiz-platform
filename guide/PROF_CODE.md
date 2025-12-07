# 1. Threads

```java
// Slide 3–4 — "Criação de threads adicionais" — PD-4 (threads-java)
public class ExtendThreadDemo extends java.lang.Thread {
    int threadNumber;
    public ExtendThreadDemo(int num) { threadNumber = num; }
    public void run() {
        System.out.println("I am thread number " + threadNumber);
        try { Thread.sleep(5000); } catch (InterruptedException e) {}
        System.out.println(threadNumber + " is finished!");
    }

    public static void main(String args[]) {
        Thread t1 = new ExtendThreadDemo(1);
        Thread t2 = new ExtendThreadDemo(2);
        t1.setDaemon(true); t2.setDaemon(true);
        t1.start(); t2.start();
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
    }
}
```

```java
// Slide 5–6 — "Criação de threads adicionais" — PD-4 (threads-java)
public class RunnableThreadDemo implements java.lang.Runnable {
    public void run() {
        System.out.println("I am an instance of the java.lang.Runnable interface");
    }
    public static void main(String args[]) {
        System.out.println("Creating runnable object");
        Runnable run = new RunnableThreadDemo();
        System.out.println("Creating first thread");
        Thread t1 = new Thread(run, "Thread 1");
        System.out.println("Creating second thread");
        Thread t2 = new Thread(run, "Thread 2");
        System.out.println("Starting both threads");
        t1.start(); t2.start();
    }
}
// atalho:
// new Thread(new RunnableThreadDemo()).start();
```

```java
// Slide 6–7 — "Criação de threads adicionais" / "Interromper Threads" — PD-4 (threads-java)
public class SleepyHead extends Thread {
    public void run() {
        System.out.println("I feel sleepy. Wake me in eight hours");
        try {
            Thread.sleep(1000 * 60 * 60 * 8);
            System.out.println("That was a nice nap");
        } catch (InterruptedException e) {
            System.err.println("Just five minutes more....");
        }
    }
    public static void main(String args[]) throws java.io.IOException {
        Thread sleepy = new SleepyHead();
        sleepy.start();
        System.out.println("Press enter to interrupt the thread");
        System.in.read();
        sleepy.interrupt();
    }
}
```

```java
// Slide 8–9 — "Parar Threads" — PD-4 (threads-java) — versão com stop() (deprecated)
public class StopMe extends Thread {
    public void run() {
        int count = 1;
        System.out.println("I can count. Watch me go!");
        while (true) {
            System.out.print(count++ + " ");
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }
    }
    public static void main(String args[]) throws java.io.IOException {
        Thread counter = new StopMe();
        counter.start();
        System.out.println("Press any enter to stop the thread counting");
        System.in.read();
        counter.stop();
    }
}
```

```java
// Slide 9 — "Parar Threads" — PD-4 (threads-java) — versão segura com flag
public class StopMe extends Thread {
    private boolean stopRunning = false;
    public void setStop(boolean stopRunning) { this.stopRunning = stopRunning; }
    public void run() {
        int count = 1;
        System.out.println("I can count. Watch me go!");
        while (true) {
            if (stopRunning) return;
            System.out.print(count++ + " ");
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }
    }
    public static void main(String args[]) throws java.io.IOException {
        Thread counter = new StopMe();
        counter.start();
        System.out.println("Press any enter to stop the thread counting");
        System.in.read();
        ((StopMe) counter).setStop(true);
    }
}
```

```java
// Slide 10 — "Operações diversas sobre threads" — PD-4 (threads-java)
public static void main(String args[]) throws java.lang.InterruptedException {
    Thread dying = new WaitForDeath();
    dying.start();
    System.out.println("Waiting for thread death");
    dying.join();
    System.out.println("Thread has died");
}
```

```java
// Slide 12–13 — "Sincronização de Threads" — PD-4 (threads-java)
public class Counter {
    private int countValue;
    public Counter() { countValue = 0; }
    public Counter(int start) { countValue = start; }
    public synchronized void increaseCount() {
        int count = countValue;
        try { Thread.sleep(5); } catch (InterruptedException ie) {}
        count = count + 1; countValue = count;
    }
    public synchronized int getCount() { return countValue; }
}

public class CountingThread implements Runnable {
    Counter myCounter; int countAmount;
    public CountingThread(Counter counter, int amount) {
        myCounter = counter; countAmount = amount;
    }
    public void run() {
        for (int i = 1; i <= countAmount; i++) {
            myCounter.increaseCount();
        }
    }
    public static void main(String args[]) throws Exception {
        Counter c = new Counter();
        Runnable runner = new CountingThread(c, 10);
        Thread t1 = new Thread(runner);
        Thread t2 = new Thread(runner);
        Thread t3 = new Thread(runner);
        t1.start(); t2.start(); t3.start();
        t1.join(); t2.join(); t3.join();
        System.out.println("Counter value is " + c.getCount());
    }
}
```

```java
// Slide 14–15 — "Sincronização de Threads" — PD-4 (threads-java)
public class SynchBlock implements Runnable {
    StringBuffer buffer;
    int counter;
    public SynchBlock() {
        buffer = new StringBuffer();
        counter = 1;
    }
    public void run() {
        synchronized (buffer) {
            System.out.print("Starting synchronized block ");
            int temp = counter++;
            String tcpMessage = "Count value is : " + temp + System.lineSeparator();
            try { Thread.sleep(100); } catch (InterruptedException ie) {}
            buffer.append(tcpMessage);
            System.out.println("... ending synchronized block");
        }
    }
    public static void main(String args[]) throws Exception {
        SynchBlock block = new SynchBlock();
        Thread t1 = new Thread(block);
        Thread t2 = new Thread(block);
        Thread t3 = new Thread(block);
        Thread t4 = new Thread(block);
        t1.start(); t2.start(); t3.start(); t4.start();
        t1.join(); t2.join(); t3.join(); t4.join();
        System.out.println(block.buffer);
    }
}
```

```java
// Slide 23–25 — "Computação Paralela Baseada em Múltiplas Threads" — PD-4 (threads-java)
public class ParallelPi extends Thread {
    private int myId, nThreads, nIntervals, myIntervals;
    private double dX, myResult;

    public ParallelPi(int myId, int nThreads, int nIntervals) {
        this.myId = myId; this.nThreads = nThreads; this.nIntervals = nIntervals;
        dX = 1.0 / (double) nIntervals;
        myResult = 0; myIntervals = 0;
    }
    public double getMyResult() { return myResult; }
    public int getMyIntervals() { return myIntervals; }

    @Override public void run() {
        if (nIntervals < 1 || nThreads < 1 || myId < 1 || myId > nThreads) return;
        for (double i = myId - 1; i < nIntervals; i += nThreads) {
            double xi = dX * (i + 0.5);
            myResult += (4.0 / (1.0 + xi * xi));
            myIntervals++;
        }
        myResult *= dX;
    }

    public static void main(String[] args) throws InterruptedException {
        ParallelPi[] threads;
        int nThreads; long nIntervals; double pi = 0.0;
        if (args.length != 2) {
            System.out.println("Sintaxe: java ParallelPi <número de intervalos> <número de threads>");
            return;
        }
        nIntervals = Integer.parseInt(args[0]);
        nThreads = Integer.parseInt(args[1]);
        threads = new ParallelPi[nThreads];
        for (int i = 0; i < nThreads; i++) {
            threads[i] = new ParallelPi(i + 1, nThreads, (int) nIntervals);
            threads[i].start();
        }
        pi = 0;
        for (ParallelPi t : threads) {
            t.join();
            pi += t.getMyResult();
        }
        System.out.println("Valor aproximado de pi: " + pi);
    }
}
```

---

# 2. Exceções

```java
// Slide 5 — "Excepções" — PD-2 (intro-java)
try {
    // A block of code where exceptions can be generated
    //...
} catch (SocketException e) {
    System.err.println("Socket error reading from host : " + e);
    System.exit(2);
} catch (Exception e) {
    System.err.println("Error : " + e);
    System.exit(1);
} finally {
    // clean up after try block, regardless of any exceptions
    //...
}
```

---

# 3. Entrada/Saída

```java
// Slide 7 — "Entrada/Saída" — PD-2 (intro-java) — FileInputStream/FileOutputStream
FileInputStream in = null;
FileOutputStream out = null;
try {
    in = new FileInputStream("xanadu.txt");
    out = new FileOutputStream("outagain.txt");
    int c;
    while ((c = in.read()) != -1) {
        out.write(c);
    }
} catch (Exception ex) {
    // Do something
} finally {
    if (in != null) in.close();
    if (out != null) out.close();
}
```

```java
// Slide 7 — "Entrada/Saída" — PD-2 (intro-java) — try-with-resources (bytes)
try (FileInputStream in = new FileInputStream("xanadu.txt");
     FileOutputStream out = new FileOutputStream("outagain.txt")) {
    int c;
    while ((c = in.read()) != -1) {
        out.write(c);
    }
} catch (Exception ex) {
    // Do something
}
```

```java
// Slide 8 — "Entrada/Saída" — PD-2 (intro-java) — Reader/Writer
public static void main(String[] args) throws IOException {
    FileReader in = null;
    FileWriter out = null;
    int c;
    try {
        in = new FileReader("xanadu.txt");
        out = new FileWriter("characteroutput.txt");
        while ((c = in.read()) != -1) {
            out.write(c);
        }
    } finally {
               if (in != null) in.close();
        if (out != null) out.close();
    }
}
```

```java
// Slide 9 — "Entrada/Saída" — PD-2 (intro-java) — BufferedReader/PrintWriter (linhas)
public static void main(String[] args) throws IOException {
    BufferedReader in = null;
    PrintWriter out = null;
    String l;
    try {
        in = new BufferedReader(new FileReader("xanadu.txt"));
        out = new PrintWriter(new FileWriter("characteroutput.txt"));
        while ((l = in.readLine()) != null)
            out.println(l);
    } finally {
        if (in != null) in.close();
        if (out != null) out.close();
        // ...
    }
}
```

```java
// Slide 9 — "Entrada/Saída" — PD-2 (intro-java) — Scanner (variações)
while ((l = in.readLine()) != null) {
    s = new Scanner(l);
    while (s.hasNext()) {
        System.out.println(s.next());
    }
}
while (s.hasNext()) {
    if (s.hasNextDouble()) {
        sum += s.nextDouble();
    } else { s.next(); }
}
```

```java
// Slide 10 — "Entrada/Saída" — PD-2 (intro-java) — Console
Console c = System.console();
if (c == null) {
    System.err.println("No console.");
    System.exit(1);
}
String login = c.readLine("Enter your login: ");
char[] oldPassword = c.readPassword("Enter your old password: ");
```

```java
// Slide 10 — "Entrada/Saída" — PD-2 (intro-java) — DataInput/DataOutput
DataInputStream in = new DataInputStream(
    new BufferedInputStream(new FileInputStream("dados.txt")));
try {
    while (true) {
        price = in.readDouble();
        unit = in.readInt();
        System.out.format("You ordered %d units at $%.2f%n", unit, price);
        total += unit * price;
    }
} catch (EOFException e) {}
DataOutputStream out = new DataOutputStream(
    new BufferedOutputStream(new FileOutputStream("dados.txt")));
out.writeDouble(10.3);
out.writeInt(34);
```

```java
// Slide 11 — "Entrada/Saída" — PD-2 (intro-java) — Serialização de objectos
public static void main(String[] args) throws IOException, ClassNotFoundException {
    ObjectOutputStream out = null;
    try {
        out = new ObjectOutputStream(new FileOutputStream("dates.bin"));
        // ... (escritas)
    } finally {
        if (out != null) out.close();
    }

    ObjectInputStream in = null;
    int nDates = 0; Calendar date = null;
    try {
        in = new ObjectInputStream(new FileInputStream("dates.bin"));
        try {
            while (true) {
                date = null;
                date = (Calendar) in.readObject();
                nDates++;
            }
        } catch (EOFException e) {}
    } finally {
        if (in != null) in.close();
    }
}
```

---

# 4. TCP (Sockets)

```java
// Slide 34 — "Protocolo TCP" — PD-3 (sockets-java) — Preparar streams em Socket
try{
// CONNECT A SOCKET TO SOME HOST MACHINE AND PORT
Socket socket = new Socket( somehost, someport );

        // CONNECT A BUFFERED READER
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream()));
        // CONNECT A PRINT STREAM
        PrintStream pstream = new PrintStream( socket.getOutputStream() );
}catch (Exception e){
        System.err.println("Error – " + e);
}
```

```java
// Slide 35 — "Protocolo TCP" — PD-3 (sockets-java) — Timeout de receção com setSoTimeout
try{
    Socket s = new Socket(...); s.setSoTimeout( 2000 );
// DO SOME READ OPERATION ....
}
catch (InterruptedIOException e){
    timeoutFlag = true; // DO SOMETHING SPECIAL LIKE SET A FLAG
}
catch (IOException e){
    System.err.println("IO error " + e);
    System.exit(0);
}
```

```java
// Slides 37–38 — "Protocolo TCP" — PD-3 (sockets-java) — DaytimeClient
import java.net.*;
import java.io.*;

public class DaytimeClient
{
    public static final int SERVICE_PORT = 5001;
    public static void main(String args[])
    {
        if (args.length != 1){
            System.out.println("Syntax – java DaytimeClient host");
            return;
        }
        // GET THE HOSTNAME OF SERVER
        String hostname = args[0];
        try {
            Socket daytime = new Socket(hostname, SERVICE_PORT);
            System.out.println ("Connection established");
            // SET THE SOCKET OPTION JUST IN CASE SERVER STALLS
            daytime.setSoTimeout( 2000 ); //ms
            // READ FROM THE SERVER
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(daytime.getInputStream()));
            System.out.println("Results : " + reader.readLine());
            // CLOSE THE CONNECTION
            daytime.close();
        }catch (IOException e){ //catches also InterruptedIOException
            System.err.println ("Error " + e);
        }
    }
}
java

```

```java
// Slides 39–40 — "Protocolo TCP" — PD-3 (sockets-java) — DaytimeServer
import java.net.*;
import java.io.*;

public class DaytimeServer
{
    public static final int SERVICE_PORT = 5001;
    public static void main(String args[])
    {
        try {
            // BIND TO THE SERVICE PORT
            ServerSocket serverManager = new ServerSocket(SERVICE_PORT);
            System.out.println("Daytime service started");
            // LOOP INDEFINITELY, ACCEPTING CLIENTS
            while(true){
                // GET THE NEXT TCP CLIENT
                Socket nextClient = serverManager.accept();
                System.out.println ("Received request from " +
                        nextClient.getInetAddress() + ":" + nextClient.getPort() );
                OutputStream out = nextClient.getOutputStream();
                PrintStream pout = new PrintStream(out);
                // WRITE THE CURRENT DATE OUT TO THE USER
                pout.println(new java.util.Date());
                // FLUSH UNSENT BYTES
                pout.flush();
                // CLOSE THE CONNECTION
                nextClient.close();
            }
        }catch (BindException e){
            System.err.println("Service already running on port " +
                    SERVICE_PORT);
        }catch (IOException e){
            System.err.println ("I/O error - " + e);
        }
    }
}

```

---

# 5. UDP (Sockets)

```java
// Slide 7 — "Protocolo UDP" — PD-3 (sockets-java) — PacketSendDemo
import java.net.*;
import java.io.*;

public class PacketSendDemo {
    public static void main(String args[]) {
        int argc = args.length;
        if (argc != 1) {
            System.out.println("Syntax :");
            System.out.println("java PacketSendDemo hostname");
            return;
        }
        String hostname = args[0];
        try {
            System.out.println("Binding to a local port");
            DatagramSocket socket = new DatagramSocket();
            System.out.println("Bound to local port " + socket.getLocalPort());
            byte[] barray = "Greetings!".getBytes();
            DatagramPacket packet = new DatagramPacket(barray, barray.length);
            System.out.println("Looking up hostname " + hostname);
            InetAddress addr = InetAddress.getByName(hostname);
            System.out.println("Hostname resolved as " + addr.getHostAddress());
            packet.setAddress(addr);
            packet.setPort(2000);
            socket.send(packet);
            System.out.println("Packet sent!");
            socket.close();
        } catch (UnknownHostException e) {
            System.err.println("Can't find host " + hostname);
        } catch (IOException e) {
            System.err.println("Error - " + e);
        }
    }
}
```

```java
// Slide 9–10 — "Protocolo UDP" — PD-3 (sockets-java) — PacketReceiveDemo
import java.net.*;
import java.io.*;

public class PacketReceiveDemo {
    public static void main(String args[]) {
        try {
            System.out.println("Binding to local port 2000");
            DatagramSocket socket = new DatagramSocket(2000);
            DatagramPacket packet = new DatagramPacket(new byte[256], 256);
            socket.receive(packet);
            InetAddress remote_addr = packet.getAddress();
            System.out.println("Sent by: " + remote_addr.getHostAddress());
            System.out.println("Sent from port: " + packet.getPort());
            String msg = new String(packet.getData(), 0, packet.getLength());
            System.out.println(msg);
            socket.close();
        } catch (IOException e) {
            System.err.println("Error - " + e);
        }
    }
}
```

```java
// Slide 12 — "Protocolo UDP" — PD-3 (sockets-java) — EchoServer (trecho principal + main)
import java.net.*;
import java.io.*;

public class EchoServer {
    public static final int SERVICE_PORT = 7000;
    public static final int BUFSIZE = 4096;
    private DatagramSocket socket = null;

    public EchoServer() {
        try {
            socket = new DatagramSocket(SERVICE_PORT);
            System.out.println("Server active on port " + socket.getLocalPort());
        } catch (Exception e) {
            System.err.println("Unable to bind port");
        }
    }
    public void serviceClients() {
        if (socket == null) return;
        byte[] buffer = new byte[BUFSIZE];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, BUFSIZE);
                socket.receive(packet);
                System.out.println("Packet received from " + packet.getAddress()
                        + ":" + packet.getPort() + " of length " + packet.getLength());
                socket.send(packet); // eco
            } catch (IOException e) {
                System.err.println("Error : " + e);
            }
        }
    }
    public static void main(String args[]) {
        EchoServer serverManager = new EchoServer();
        serverManager.serviceClients();
    }
}
```

```java
// Slide 13 — "Protocolo UDP" — PD-3 (sockets-java) — EchoClient
import java.net.*;
import java.io.*;

public class EchoClient {
    public static final int SERVICE_PORT = 7000;
    public static final int BUFSIZE = 256;

    public static void main(String args[]) {
        if (args.length != 1) {
            System.err.println("Syntax - java EchoClient hostname");
            return;
        }
        String hostname = args[0];
        InetAddress addr;
        try {
            addr = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            System.err.println("Unable to resolve host");
            return;
        }
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(2 * 1000);
            for (int i = 1; i <= 10; i++) {
                String tcpMessage = "Packet number " + i;
                byte[] sendbuf = tcpMessage.getBytes();
                DatagramPacket sendPacket =
                    new DatagramPacket(sendbuf, sendbuf.length, addr, SERVICE_PORT);
                System.out.println("Sending packet to " + hostname);
                socket.send(sendPacket);
                System.out.print("Waiting for packet.... ");
                byte[] recbuf = new byte[BUFSIZE];
                DatagramPacket receivePacket = new DatagramPacket(recbuf, BUFSIZE);
                boolean timeout = false;
                try {
                    socket.receive(receivePacket);
                } catch (InterruptedIOException e) {
                    timeout = true;
                }
                if (!timeout) {
                    System.out.println("Packet received!");
                    String msg = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    System.out.println(msg);
                } else {
                    System.out.println("packet lost!");
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            }
        } catch (IOException e) {
            System.err.println("Socket error " + e);
        }
    }
}
```
