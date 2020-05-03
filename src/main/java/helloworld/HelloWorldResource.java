package helloworld;

import com.codahale.metrics.annotation.Timed;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Path("/hello-world")
@Produces(MediaType.TEXT_HTML)
public class HelloWorldResource {
    private final String template;
    private final String defaultName;
    private final AtomicLong counter;

    private static final String FILE_PATH = "somevideo.mp4";
    private final int chunk_size = 1024 * 1024 * 2; // 2 MB chunks

    public HelloWorldResource(String template, String defaultName) {
        this.template = template;
        this.defaultName = defaultName;
        this.counter = new AtomicLong();
    }

    @GET
    @Timed
    public HelloWorldView sayHello(@QueryParam("name") Optional<String> name) {
        final String value = template + "and also" + name.orElse(defaultName);
        //return new Saying(counter.incrementAndGet(), value);
        return new HelloWorldView(value);
    }

    @GET
    @Produces("video/mp4")
    @Path("/stream")
    public Response stream(@HeaderParam("Range") String range) throws Exception {
        URL url = this.getClass().getResource( FILE_PATH );
        System.out.println(url);
        File file = new File( "/Users/bbonafil/IdeaProjects/JavaWebVideoDemo/target/classes/helloworld/somevideo.mp4" );
        System.out.println(url.getFile());
        return buildStream( file, range );
    }

    /**
     * @param asset Media file
     * @param range range header
     * @return Streaming output
     * @throws Exception IOException if an error occurs in streaming.
     */
    private Response buildStream( final File asset, final String range ) throws Exception {
        // range not requested: firefox does not send range headers
        if ( range == null ) {

            StreamingOutput streamer = output -> {
                try (FileChannel inputChannel = new FileInputStream( asset ).getChannel();
                     WritableByteChannel outputChannel = Channels.newChannel( output ) ) {

                    inputChannel.transferTo( 0, inputChannel.size(), outputChannel );
                }
                catch( IOException io ) {
                    log.info( io.getMessage() );
                }
            };

            return Response.ok( streamer )
                    .status( Response.Status.OK )
                    .header( HttpHeaders.CONTENT_LENGTH, asset.length() )
                    .build();
        }

        log.info( "Requested Range: " + range );

        String[] ranges = range.split( "=" )[1].split( "-" );

        int from = Integer.parseInt( ranges[0] );

        // Chunk media if the range upper bound is unspecified
        int to = chunk_size + from;

        if ( to >= asset.length() ) {
            to = (int) ( asset.length() - 1 );
        }

        // uncomment to let the client decide the upper bound
        // we want to send 2 MB chunks all the time
        //if ( ranges.length == 2 ) {
        //    to = Integer.parseInt( ranges[1] );
        //}

        final String responseRange = String.format( "bytes %d-%d/%d", from, to, asset.length() );

        log.info( "Response Content-Range: " + responseRange + "\n");

        final RandomAccessFile raf = new RandomAccessFile( asset, "r" );
        raf.seek( from );

        final int len = to - from + 1;
        final MediaStreamer mediaStreamer = new MediaStreamer( len, raf );

        return Response.ok( mediaStreamer )
                .status( Response.Status.PARTIAL_CONTENT )
                .header( "Accept-Ranges", "bytes" )
                .header( "Content-Range", responseRange )
                .header( HttpHeaders.CONTENT_LENGTH, mediaStreamer. getLenth() )
                .header( HttpHeaders.LAST_MODIFIED, new Date( asset.lastModified() ) )
                .build();
    }
}
