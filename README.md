## Reliable-UDP-Protocol
This project creates a reliable communication with the [httpc](https://github.com/DhwaniSondhi/HTTP-Client-Implementation) client and [httpfs](https://github.com/DhwaniSondhi/File-Server-Application) server using UDP protocol. It uses [Selective-Repeat ARQ technique](https://en.wikipedia.org/wiki/Selective_Repeat_ARQ) on top of unreliable UDP for making it work as a reliable TCP protocol. It is tested with changes in values of both drop rate and delay.

### Description
***Router***:
Instead of sending/receiving network packets directly between a client and a server, both of them have to send and receive packets via the router. The purpose of this infrastructure is to simulate and control the unreliable characteristic of the UDP protocol. In the figure, both applications A and B send and receive packets to/from the router.

<img src="https://github.com/DhwaniSondhi/Reliable-UDP-Protocol/blob/master/images/1.PNG" width="600" height="300"/><br/>
<br/>***Message Structure***:
To interact properly with the router, all the packets (e.g. UDP message) should follow the following structure; otherwise, the router may reject or fail to dispatch it to a proper destination.

<img src="https://github.com/DhwaniSondhi/Reliable-UDP-Protocol/blob/master/images/2.PNG" width="500" height="350"/>

### Flow Example
1. Router is running at port 3000 at the host 192.168.2.10
2. Server is running at port 8007 at the host 192.168.2.3
3. Client is running at port 41830 (uses an ephemeral port) at the host 192.168.2.125
<img src="https://github.com/DhwaniSondhi/Reliable-UDP-Protocol/blob/master/images/3.PNG" width="600" height="400"/>

[Please click here for more information.](https://github.com/DhwaniSondhi/Reliable-UDP-Protocol/blob/master/Project%20Description.pdf)
