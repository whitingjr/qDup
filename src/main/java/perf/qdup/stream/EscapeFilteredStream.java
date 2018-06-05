package perf.qdup.stream;

import perf.yaup.Sets;

import java.io.IOException;
import java.util.Set;

/**
 * need to return length of full match or length or partial match
 * just return length of match and then check if last char is m
 */
public class EscapeFilteredStream extends MultiStream {

    private static final Set<Character> CONTROL_SUFFIX = Sets.of(
            'A',//cursor up
            'B',//cursor down
            'C',//cursor forward
            'D',//cursor back
            'E',//cursor next line
            'F',//cursor previous line
            'G',//cursor horizontal absolute
            'H',//cursor position
            'J',//erase display
            'K',//erase in line
            'S',//scroll up
            'T',//scroll down
            'f',//horozontal,vertical position
            'm',//graphical rendering
            'i',//aux port on
            'n',//device status
            's',//save cursor position
            'u' //restore cursor position
            );

    private byte[] buffered;
    private int writeIndex = 0;

    public EscapeFilteredStream(){
        buffered = new byte[20*1024];
    }
    protected void superWrite(byte b[], int off, int len) throws IOException {
        super.write(b,off,len);
    }
    @Override
    public void write(byte b[]) throws IOException {
        write(b,0,b.length);
    }
    @Override
    public void write(byte b[], int off, int len) throws IOException {
        int flushIndex = 0;
        int trailingEscapeIndex = Integer.MAX_VALUE;
        if(writeIndex + len > buffered.length){
            int needed = writeIndex+len - buffered.length;
            byte[] newBuffer = new byte[buffered.length+needed];
            System.arraycopy(buffered,0,newBuffer,0,writeIndex);
            buffered = newBuffer;
        }
        System.arraycopy(b,off,buffered,writeIndex,len);
        writeIndex+=len;

        for(int currentIndex = 0; currentIndex < writeIndex; currentIndex ++) {
            boolean filtered = false;
            do {
                filtered = false;
                int escapeLength = escapeLength(buffered, currentIndex, writeIndex - currentIndex);
                if (isEscaped(buffered, currentIndex, escapeLength)) {//is full match, flush to super
                    filtered = true;
                } else if (escapeLength > 0) {//match reached end of buffer
                    if (trailingEscapeIndex > currentIndex) {
                        trailingEscapeIndex = currentIndex;
                    }
                }
                if (filtered) {
                    //broken escape sequences are not supported in terminals
                    if (flushIndex < currentIndex) {
                        superWrite(buffered, flushIndex, currentIndex - flushIndex);
                    }
                    currentIndex += escapeLength;
                    flushIndex = currentIndex;
                    trailingEscapeIndex = Integer.MAX_VALUE;
                }
            } while (filtered);
        }
        if(trailingEscapeIndex < Integer.MAX_VALUE){//flush from flushIndex to trailingPrefixIndex
            if(trailingEscapeIndex-flushIndex>0){
                superWrite(buffered, flushIndex, trailingEscapeIndex - flushIndex);
            }
            flushIndex = trailingEscapeIndex;
        }else{// no matches and no potential matches, flush everything
            superWrite(buffered,flushIndex,writeIndex-flushIndex);
            flushIndex = writeIndex-1;
            flushIndex = writeIndex;//TODO testing if fixes double write


            if(flushIndex > 0 ){
                System.arraycopy(buffered,flushIndex,buffered,0,writeIndex-flushIndex);
                writeIndex=writeIndex-flushIndex;
            }
        }
    }
    //basically just makes sure we have \u001b[...m
    public boolean isEscaped(byte b[],int off,int len){
        return len>3 && b[off]==27 && b[off+1]=='[' && CONTROL_SUFFIX.contains((char)b[off+len-1]);
    }
    //return length of match up to len or 0 if match failed
    public int escapeLength(byte b[], int off, int len){
        boolean matching = true;
        int rtrn = 0;
        if(b[off]==27 && 1<=len && b[off+1]=='['){//\003[
            rtrn=2;//the initial 2 matched characters
            while(matching && rtrn<len){
                while(rtrn < len && b[off+rtrn]>='0' && b[off+rtrn]<='9'){//digit
                    rtrn++;
                }
                //not an integer, if ; continue
                if(rtrn<len){
                    if( b[off+rtrn]==';'){
                        rtrn++;
                    }else if (CONTROL_SUFFIX.contains((char)b[off+rtrn])){
                        rtrn++;//we matched this character too
                        matching=false;//end of match
                    }else{//false alarm, not a valid escape character
                        rtrn=0;
                        matching=false;
                    }
                }else{
                    matching=false;//stop the match at end of len
                }
            }
        }else{
            rtrn = 0;
        }
        return rtrn;
    }
}
