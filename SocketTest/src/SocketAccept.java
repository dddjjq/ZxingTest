import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketAccept {
	public static void main(String[] args) {
		String resultOk = "OK";
		ServerSocket serverSocket = null;
		try {
			while (true) {
				serverSocket = new ServerSocket(2001);
				Socket socket = serverSocket.accept();
				InputStream inputStream = socket.getInputStream();
				OutputStream outputStream = socket.getOutputStream();
				byte buffer[] = new byte[1024];
				int length;
				StringBuilder sb = new StringBuilder();
				while ((length = inputStream.read(buffer)) != -1) {
					sb.append(new String(buffer, 0, length));
				}
				outputStream.write(resultOk.getBytes());
				outputStream.flush();
				System.out.println(sb.toString());
				inputStream.close();
				outputStream.close();
				serverSocket.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
