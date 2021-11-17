#include <Arduino.h>
#include <SPI.h>
#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "Adafruit_BluefruitLE_UART.h"

#include "BluefruitConfig.h"

#if SOFTWARE_SERIAL_AVAILABLE
  #include <SoftwareSerial.h>
#endif

#define X_EN 20 // X is channel 2, X enable controlled by SDA pin
#define Y_EN 6 // Y is channel 3, Y enable controlled by pin 13
#define Z_EN 5 // Z is channel 4, Z enable controlled by pin 12

#define X_DIR 21 // X direction controlled by SCL pin
#define Y_DIR 13  // Y direction controlled by pin 6
#define Z_DIR A2  // Z direction controlled by pin 5

#define X_DIR_POSITIVE 1 // direction of positive movement. 0 moves away from motor, 1 moves toward motor
#define Y_DIR_POSITIVE 1
#define Z_DIR_POSITIVE 0

#define X_STP 12  // X step controlled by pin 9~
#define Y_STP 11 // Y step controlled by pin 11~
#define Z_STP 10 // Z step controlled by pin 10~

#define LED_WHITE A1 // white LED controlled by  pin A5
#define LED_IR A0 // IR LED controlled by pin A3--potentiometer controls brightness

#define LIMITX A4 // limit switch detected by pin A4
#define LIMITY A3 // limit switch detected by pin A3 for z axis
#define LIMITZ A5 // limit switch detected by pin A3 for z axis



//VARIABLE DEFINITIONS%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
volatile long currentPositionX = 0;//current recorded position of stage
volatile long currentPositionY = 0;
volatile long currentPositionZ = 0;

volatile long requestedPositionX = 0;//position the stage is supposed to be. motors disabled if value matches currentPosition
volatile long requestedPositionY = 0;
volatile long requestedPositionZ = 0;

volatile char limitENdisable = 1; //disables all motors when limit switch is tripped. set to 0 to allow motors to enable. requestedPosition will still not work until limit switch is no longer tripped





unsigned long previousMillis = 0;        // will store last time LED was updated

const int DELAYTIME = 1000;
const long interval = 10;           // interval at which to blink (milliseconds)
int counterState = LOW;             // ledState used to set the LED
int ir_intensity=800;
int engageLimits=0;
int xPos;
  int yPos;
  int zPos;


/*=========================================================================
    APPLICATION SETTINGS

    FACTORYRESET_ENABLE       Perform a factory reset when running this sketch
   
                              Enabling this will put your Bluefruit LE module
                              in a 'known good' state and clear any config
                              data set in previous sketches or projects, so
                              running this at least once is a good idea.
   
                              When deploying your project, however, you will
                              want to disable factory reset by setting this
                              value to 0.  If you are making changes to your
                              Bluefruit LE device via AT commands, and those
                              changes aren't persisting across resets, this
                              is the reason why.  Factory reset will erase
                              the non-volatile memory where config data is
                              stored, setting it back to factory default
                              values.
       
                              Some sketches that require you to bond to a
                              central device (HID mouse, keyboard, etc.)
                              won't work at all with this feature enabled
                              since the factory reset will clear all of the
                              bonding data stored on the chip, meaning the
                              central device won't be able to reconnect.
    MINIMUM_FIRMWARE_VERSION  Minimum firmware version to have some new features
    MODE_LED_BEHAVIOUR        LED activity, valid options are
                              "DISABLE" or "MODE" or "BLEUART" or
                              "HWUART"  or "SPI"  or "MANUAL"
    -----------------------------------------------------------------------*/
    #define FACTORYRESET_ENABLE         1
    #define MINIMUM_FIRMWARE_VERSION    "0.6.6"
    #define MODE_LED_BEHAVIOUR          "MODE"
/*=========================================================================*/

// Create the bluefruit object, either software serial...uncomment these lines
/*
SoftwareSerial bluefruitSS = SoftwareSerial(BLUEFRUIT_SWUART_TXD_PIN, BLUEFRUIT_SWUART_RXD_PIN);

Adafruit_BluefruitLE_UART ble(bluefruitSS, BLUEFRUIT_UART_MODE_PIN,
                      BLUEFRUIT_UART_CTS_PIN, BLUEFRUIT_UART_RTS_PIN);
*/

/* ...or hardware serial, which does not need the RTS/CTS pins. Uncomment this line */
// Adafruit_BluefruitLE_UART ble(BLUEFRUIT_HWSERIAL_NAME, BLUEFRUIT_UART_MODE_PIN);

/* ...hardware SPI, using SCK/MOSI/MISO hardware SPI pins and then user selected CS/IRQ/RST */
Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

/* ...software SPI, using SCK/MOSI/MISO user-defined SPI pins and then user selected CS/IRQ/RST */
//Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_SCK, BLUEFRUIT_SPI_MISO,
//                             BLUEFRUIT_SPI_MOSI, BLUEFRUIT_SPI_CS,
//                             BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);


// A small helper
void error(const __FlashStringHelper*err) {
  Serial.println(err);
  while (1);
}

/**************************************************************************/
/*!
    @brief  Sets up the HW an the BLE module (this function is called
            automatically on startup)
*/
/**************************************************************************/
class Flasher
{
  // Class Member Variables
  // These are initialized at startup
  long internalDelayTime;
  int internaldir1;
  int internalEnpin;
  int internalSteppin;
  int internalDirpin;

  //int internaldir2;
  //int internaldir3;

  // Constructor - creates a Flasher
  // and initializes the member variables and state
  public:
  Flasher(int enpin, int dirpin, int steppin)
  {

    //internalDelayTime=delayTime;
    internalEnpin=enpin;
    internalSteppin=steppin;
    internalDirpin=dirpin;

  }
  void setVars(int stopMoving,int dir1, /*int dir2, int dir3,*/ int delayTime) {
    previousMillis = 0;
    internalDelayTime=delayTime*500;
    internaldir1=dir1;
    //internaldir2=dir2;
    //internaldir3=dir3;
    //internalDelayTime=delayTime;
    if(internalDelayTime > 0 && stopMoving==0){
      digitalWrite(internalEnpin,LOW);
    }
    else digitalWrite(internalEnpin,HIGH);


    if (dir1==0) digitalWrite(internalDirpin,LOW);
    else digitalWrite(internalDirpin,HIGH);

    /*if(internalDelayTime > 0){
      digitalWrite(Y_EN,LOW);
    }
    if(internalDelayTime > 0){
      digitalWrite(Z_EN,LOW);
    }*/
    //digitalWrite(X_DIR,internaldir1);
    //digitalWrite(Y_DIR,internaldir2);
    //digitalWrite(Z_DIR,internaldir3);
  }

  void Update()
  {

    // check to see if it's time to change the state of the LED

    //while(true){

      unsigned long currentMillis = micros();

      if (currentMillis - previousMillis >= internalDelayTime) {
          previousMillis = currentMillis;

          // if the LED is off turn it on and vice-versa:
          if (counterState == LOW) {
            counterState = HIGH;
          } else {
            counterState = LOW;
        }
        digitalWrite(internalSteppin,counterState);
        //digitalWrite(Z_STP,counterState);
        //digitalWrite(Y_STP,counterState);
     //}
    }
  //digitalWrite(X_EN,HIGH);
  //digitalWrite(Y_EN,HIGH);
  //digitalWrite(Z_EN,HIGH);

  }
};


Flasher led1(X_EN,X_DIR,X_STP);
Flasher led2(Y_EN,Y_DIR,Y_STP);
Flasher led3(Z_EN,Z_DIR,Z_STP);

void setup(void)
{
  //led1.setVars(1,1,1,1,1);

  //pinMode(9, OUTPUT);
  //pinMode(10, OUTPUT);
  //digitalWrite(9,HIGH);
  //digitalWrite(10,HIGH);
  //Serial.begin(9600);
  //while (!Serial) {
   // ; // wait for serial port to connect. Needed for native USB port only
  //}
  pinMode(X_EN,OUTPUT);
  pinMode(Y_EN,OUTPUT);
  pinMode(Z_EN,OUTPUT);
  pinMode(X_DIR,OUTPUT);
  pinMode(Y_DIR,OUTPUT);
  pinMode(Z_DIR,OUTPUT);
  pinMode(X_STP,OUTPUT);
  pinMode(Y_STP,OUTPUT);
  pinMode(Z_STP,OUTPUT);
  digitalWrite(LED_WHITE,HIGH);//initialize white LED off.  write LOW to turn on
  analogWrite(LED_IR,1023);//initialize IR LED off.  write HIGH to turn on.  note potentiometer position if IR does not turn on.
  pinMode(LED_WHITE,OUTPUT);
  //pinMode(LED_IR,OUTPUT);
  //pinMode(LIMIT,INPUT);
  //pinMode(LIMITZ,INPUT);

  //digitalWrite(LIMIT,HIGH);//set pullup resistor. Pin will be pulled low if any of the limit switches close
  //digitalWrite(LIMITZ,HIGH);//set pullup resistor. Pin will be pulled low if any of the limit switches close

  pinMode(LED_WHITE,OUTPUT);
  //pinMode(LED_IR,OUTPUT);
  pinMode(LIMITX,INPUT_PULLUP);
  pinMode(LIMITY,INPUT_PULLUP);
  pinMode(LIMITZ,INPUT_PULLUP);


  digitalWrite(LED_WHITE,HIGH);//initialize white LED off.  write LOW to turn on
  //analogWrite(LED_IR,1023);//initialize IR LED off.  write HIGH to turn on.  note potentiometer position if IR does not turn on.

  //digitalWrite(LIMIT,HIGH);//set pullup resistor. Pin will be pulled low if any of the limit switches close
    //digitalWrite(LIMITZ,HIGH);//set pullup resistor. Pin will be pulled low if any of the limit switches close

  digitalWrite(X_EN,HIGH);//initialize all three motors disabled. write LOW to enable
  digitalWrite(Y_EN,HIGH);
  digitalWrite(Z_EN,HIGH);

  digitalWrite(X_STP,LOW);//initialize step signals low. rising edge of pulse triggers single step.  microsteps disabled.  200 steps = 1mm.  Max 1000 steps per second
  digitalWrite(Y_STP,LOW);
  digitalWrite(Z_STP,LOW);
  //step_motor(100,1,0,0,0,0);

  //while (!Serial);  // required for Flora & Micro
  //delay(500);

  //Serial.begin(115200);
  //Serial.println(F("Adafruit Bluefruit Command <-> Data Mode Example"));
  //Serial.println(F("------------------------------------------------"));

  /* Initialise the module */
  //Serial.print(F("Initialising the Bluefruit LE module: "));

  if ( !ble.begin(VERBOSE_MODE) )
  {
    //error(F("Couldn't find Bluefruit, make sure it's in CoMmanD mode & check wiring?"));
  }
  //Serial.println( F("OK!") );

  if ( FACTORYRESET_ENABLE )
  {
    /* Perform a factory reset to make sure everything is in a known state */
    //Serial.println(F("Performing a factory reset: "));
    if ( ! ble.factoryReset() ){
      //error(F("Couldn't factory reset"));
    }
  }

  /* Disable command echo from Bluefruit */
  ble.echo(false);

  //Serial.println("Requesting Bluefruit info:");
  /* Print Bluefruit information */
  //ble.info();

  //Serial.println(F("Please use Adafruit Bluefruit LE app to connect in UART mode"));
  //Serial.println(F("Then Enter characters to send to Bluefruit"));
  //Serial.println();

  ble.verbose(false);  // debug info is a little annoying after this point!


  //homing_sequence();
  /* Wait for connection */
  while (! ble.isConnected()) {
      //delay(500);
  }

  //Serial.println(F("******************************"));

  // LED Activity command is only supported from 0.6.6
  if ( ble.isVersionAtLeast(MINIMUM_FIRMWARE_VERSION) )
  {
    // Change Mode LED Activity
    //Serial.println(F("Change LED activity to " MODE_LED_BEHAVIOUR));
    ble.sendCommandCheckOK("AT+HWModeLED=" MODE_LED_BEHAVIOUR);
  }

  // Set module to DATA mode
  //Serial.println( F("Switching to DATA mode!") );
  ble.setMode(BLUEFRUIT_MODE_DATA);


}

/**************************************************************************/
/*!
    @brief  Constantly poll for new command or response data
*/
/**************************************************************************/
void loop(void)
{


    //if (!digitalRead(LIMITZ))
      //Serial.println(digitalRead(LIMITZ));
    led1.Update();
    led2.Update();
    led3.Update();

  //ble.write("hi");

  // Check for user input
  char n, inputs[BUFSIZE+1];

  /*if (Serial.available())
  {
    n = Serial.readBytes(inputs, BUFSIZE);
    inputs[n] = 0;
    // Send characters to Bluefruit
    Serial.print("Sending: ");
    Serial.println(inputs);

    // Send input data to host via Bluefruit
    ble.print(inputs);
    ble.print("hi");
  }*/
  //Serial.print(ble.available());
  // Echo received data
  while ( ble.available() )
  {
    int data0 = ble.read();
    int data1 = ble.read();
    int data2 = ble.read();
    //ble.write(data0);
    //ble.write(data1);
    //ble.write(data0);

    char charBuf[256];
    memset(charBuf, 0, 256);

    //digitalWrite(LED_WHITE,HIGH);//initialize white LED off.  write LOW to turn on
    //digitalWrite(LED_IR,LOW);//initialize IR LED off.  write HIGH to turn on.  note potentiometer position if IR does not turn on.
    if (data0==0x01){
      if (data1==0x01){

        analogWrite(LED_IR,ir_intensity);
        ble.write("ir led on");
      }
      else if (data1==0x02){
        analogWrite(LED_IR,1023);
        ble.write("ir led off");
      }
      else if (data1=0x03){
        analogWrite(LED_IR,1023);
        delay(700); //300 for borked phone
        digitalWrite(LED_WHITE,LOW);
        delay(15); //should be 15
        digitalWrite(LED_WHITE,HIGH);
        delay(3000);
        analogWrite(LED_IR,ir_intensity);
        ble.write("bright led flash");
      }
      else if (data1=0x04){
        digitalWrite(10,HIGH);
        ble.write("bright led off");
      }
    }
    if (data0==0x03){
      if (data1==0)  led1.setVars(1,data2,0);
      else led1.setVars(0,data2,data1);
      //Serial.print ("doing something");
      //ble.write("got something");
      //set_velocity(data1,data2,0,0,0,0);
      //set_velocity(10,1,0,0,0,0);
      //step_motor(data1,data2,0,0,0,0);
    }

    if (data0==0x04) {
      //Serial.print("doing something else");
      //ble.write("got something else");
      int comb;
      comb=data2;
      comb=comb*256;
      comb|=data1;
       step_motor_fried(comb,0,0,0,0,0);

       String("Moved in -x " + String(comb)).toCharArray(charBuf, 256);
        ble.write(charBuf);
    }

    if (data0==0x05) {
      //Serial.print("doing something else");
      //ble.write("got something else");
      int comb;
      comb=data2;
      comb=comb*256;
      comb|=data1;
       step_motor_fried(comb,1,0,0,0,0);

      String("Moved in +x " + String(comb)).toCharArray(charBuf, 256);
      ble.write(charBuf);

    }

    if (data0==0x06){
      if (data1==0)  led2.setVars(1,data2,0);
      else led2.setVars(0,data2,data1);
    }

    if (data0==0x07) {
      //Serial.print("doing something else");
      //ble.write("got something else");
      int comb;
      comb=data2;
      comb=comb*256;
      comb|=data1;
       step_motor_fried(0,0,comb,0,0,0);

      String("Moved in -y " + String(comb)).toCharArray(charBuf, 256);
      ble.write(charBuf);

    }

    if (data0==0x08) {
      //Serial.print("doing something else");
      //ble.write("got something else");
      int comb;
      comb=data2;
      comb=comb*256;
      comb|=data1;
       step_motor_fried(0,0,comb,1,0,0);

      String("Moved in +y " + String(comb)).toCharArray(charBuf, 256);
      ble.write(charBuf);

    }
    if (data0==0x09){
      if (data1==0)  led3.setVars(1,data2,0);
      else led3.setVars(0,data2,data1);
    }

    if (data0==0x10) {
      //Serial.print("doing something else");
      //ble.write("got something else");
      int comb;
      comb=data2;
      comb=comb*256;
      comb|=data1;
       step_motor_fried(0,0,0,0,comb,0);

      String("Moved in -z " + String(comb)).toCharArray(charBuf, 256);
      ble.write(charBuf);

    }

    if (data0==0x11) {
      //Serial.print("doing something else");
      //ble.write("got something else");
      int comb;
      comb=data2;
      comb=comb*256;
      comb|=data1;
      step_motor_fried(0,0,0,0,comb,1);
      String("Moved in +z " + String(comb)).toCharArray(charBuf, 256);
      ble.write(charBuf);

    }

    if (data0==0x12) {

      // TODO: Only convert from 0/1 to -1/1 and back once
      int xSigned = data1 - 127;
      int ySigned = data2 - 127;
      int x = abs(xSigned);
      int y = abs(ySigned);
      step_motor_fried(x,xSigned > 0 ? 1 : 0,y,ySigned > 0 ? 1 : 0,0,0);
    }

    if (data0==0x44){
      homing_sequence();

    }
    if (data0==0x45){
      //int breakpoint = issue_reverse();
      char charBuf[128];

      String("x pos is " + String(xPos)).toCharArray(charBuf, 128);
      ble.write(charBuf);
      String("y pos is " + String(yPos)).toCharArray(charBuf, 128);
      ble.write(charBuf);
      String("z pos is " + String(zPos)).toCharArray(charBuf, 128);
      ble.write(charBuf);

    }
      //ble.write("i am in the homing method! data0 is ");
      //char blah=char(data0);
      //ble.write(data0);
      /*requestedPositionX = -100000; //push X axis all the way to its negative limit
      while(digitalRead(LIMIT)); //wait until X axis reaches the negative limit (triggers limit switch)

      currentPositionX = 0;
      requestedPositionX = 0; //while axis movement is locked from limit switch, set X values such that X will not move when limit releases

      limitENdisable = 0; // stop limit switch from disabling motors

      digitalWrite(X_EN,LOW); // enable X motor
      digitalWrite(X_DIR,X_DIR_POSITIVE); // set movement direction to positive
      while(!digitalRead(LIMIT)){ //slowly step X in the positive direction until limit switch releases
        digitalWrite(X_STP,HIGH);
        delay(1);
        digitalWrite(X_STP,LOW);
        delay(10);
      }
      currentPositionX = 0; // set current position as absolute 0 position in X
      digitalWrite(X_EN,HIGH); // disable X motor


      limitENdisable = 1; // allow limit switch to disable motors
      requestedPositionY = -100000; // repeat processes for Y axis
      while(digitalRead(LIMIT));

      requestedPositionY = 0;
      currentPositionY = 0;

      limitENdisable = 0;

      digitalWrite(Y_EN,LOW);
      digitalWrite(Y_DIR,Y_DIR_POSITIVE);
      while(!digitalRead(LIMIT)){
        digitalWrite(Y_STP,HIGH);
        delay(1);
        digitalWrite(Y_STP,LOW);
        delay(10);
      }
      currentPositionY = 0;
      digitalWrite(Y_EN,HIGH);



      limitENdisable = 1;
      requestedPositionZ = 100000; // repeat processes for Z axis in positive direction
      while(digitalRead(LIMIT));

      requestedPositionZ = 30000;
      currentPositionZ = 30000;
      limitENdisable = 0;

      digitalWrite(Z_EN,LOW);
      digitalWrite(Z_DIR,!Z_DIR_POSITIVE);
      while(!digitalRead(LIMIT)){
        digitalWrite(Z_STP,HIGH);
        delay(1);
        digitalWrite(Z_STP,LOW);
        delay(10);
      }
      currentPositionZ = 30000;
      digitalWrite(Z_EN,HIGH);
      limitENdisable = 1;



      requestedPositionX = 15000;
      requestedPositionZ = 15000;

      while(requestedPositionX != currentPositionX);
      requestedPositionY = 10000;
    }*/
  }
}

void set_velocity(int velocity1, int dir1, int velocity2,int dir2, int velocity3, int dir3){
  int counter = 0;
  int firstLoop=1;          // save the last time you blinked the LED;
  int newCall=1;
  if(velocity1 > 0){
    digitalWrite(X_EN,LOW);
  }
  if(velocity2 > 0){
    digitalWrite(Y_EN,LOW);
  }
  if(velocity3 > 0){
    digitalWrite(Z_EN,LOW);
  }
  digitalWrite(X_DIR,dir1);
  digitalWrite(Y_DIR,dir2);
  digitalWrite(Z_DIR,dir3);
  long intervalVelocity1 = velocity1; // interval at which to blink (milliseconds)
      firstLoop=1;
      newCall=0;
      //if (newCall==1) break;
      unsigned long currentMillis = millis();

      if (currentMillis - previousMillis >= intervalVelocity1) {
          previousMillis = currentMillis;

          // if the LED is off turn it on and vice-versa:
          if (counterState == LOW) {
            counterState = HIGH;
          } else {
            counterState = LOW;
        }
        digitalWrite(X_STP,counterState);
        digitalWrite(Z_STP,counterState);
        digitalWrite(Y_STP,counterState);
        counter++;
    }
}

/*void step_motor(int numstep1, int dir1, int numstep2, int dir2, int numstep3, int dir3){
  int counter = 0;


  if(numstep1 > 0){
    digitalWrite(X_EN,LOW);
  }
  if(numstep2 > 0){
    digitalWrite(Y_EN,LOW);
  }
  if(numstep3 > 0){
    digitalWrite(Z_EN,LOW);
  }

  digitalWrite(X_DIR,dir1);
  digitalWrite(Y_DIR,dir2);
  digitalWrite(Z_DIR,dir3);

    while((counter <= numstep1 || counter <= numstep2 || counter <= numstep3) && digitalRead(LIMIT)){

      unsigned long currentMillis = millis();

      if (currentMillis - previousMillis >= interval) {
          // save the last time you blinked the LED
          previousMillis = currentMillis;

          // if the LED is off turn it on and vice-versa:
          if (counterState == LOW) {
            counterState = HIGH;
          } else {
            counterState = LOW;
        }
        digitalWrite(X_STP,counterState);
        digitalWrite(Z_STP,counterState);
        digitalWrite(Y_STP,counterState);
         counter++;
      }
    }
    digitalWrite(X_EN,HIGH);
    digitalWrite(Y_EN,HIGH);
    digitalWrite(Z_EN,HIGH);
}
*/
void step_motor_fried(int numstep1, int dir1, int numstep2, int dir2, int numstep3, int dir3){
  int counter = 0;

  int xStepAdd = dir1 == 0 ? -1 : 1;
  int yStepAdd = dir2 == 0 ? -1 : 1;
  int zStepAdd = dir3 == 0 ? -1 : 1;

  if(numstep1 > 0){
    digitalWrite(X_EN,LOW);
  }
  if(numstep2 > 0){
    digitalWrite(Y_EN,LOW);
  }
  if(numstep3 > 0){
    digitalWrite(Z_EN,LOW);
  }

  digitalWrite(X_DIR,dir1);
  digitalWrite(Y_DIR,dir2);
  digitalWrite(Z_DIR,dir3);

    while((counter <= numstep1 || counter <= numstep2 || counter <= numstep3) /*&& digitalRead(LIMIT)*/){
      counter++;

      if(counter <= numstep1){
        digitalWrite(X_STP,HIGH);
      }
      if(counter <= numstep2){
        digitalWrite(Y_STP,HIGH);
      }
      if(counter <= numstep3){
        digitalWrite(Z_STP,HIGH);
      }

      delayMicroseconds(DELAYTIME);

      if(counter <= numstep1){
        digitalWrite(X_STP,LOW);
        if (!digitalRead(LIMITX) && engageLimits==1) {

          if(ble.isConnected()) {
            ble.write("hit x limit");
          }
          //Serial.println("hit x limit");
          //Serial.println(xPos);
          // 5000 is just an arbitrary number comfortably between the two ends
          if (xPos<=5000) {dir1=1; xStepAdd=1;} else { dir1=0; xStepAdd=-1;}
          digitalWrite(X_DIR,dir1);

          while(!digitalRead(LIMITX)){
            digitalWrite(X_STP,HIGH);
            delay(10);
            digitalWrite(X_STP,LOW);
            xPos=xPos+xStepAdd;
            delay(10);

          }
          for (int i=0; i<10;i++) {
            digitalWrite(X_STP,HIGH);
            delay(10);
            digitalWrite(X_STP,LOW);
            xPos=xPos+xStepAdd;

            delay(10);
          }


          //break;
          numstep1=0;

        }
        xPos=xPos+xStepAdd;

      }
      if(counter <= numstep2){
        //Serial.println(digitalRead(LIMITX));

        digitalWrite(Y_STP,LOW);
        if (!digitalRead(LIMITY) && engageLimits==1) {

          if(ble.isConnected()) {
            ble.write("hit y limit");
          }
          //Serial.println("hit y limit");
          //Serial.println(yPos);
          // 5000 is just an arbitrary number comfortably between the two ends
          if (yPos<=5000) {dir2=1; yStepAdd=1;} else { dir2=0; yStepAdd=-1;}
            digitalWrite(Y_DIR,dir2);

          while(!digitalRead(LIMITY)){
            digitalWrite(Y_STP,HIGH);
            delay(10);
            digitalWrite(Y_STP,LOW);
            yPos=yPos+yStepAdd;
            delay(10);
          }
          for (int i=0; i<10;i++) {
            digitalWrite(Y_STP,HIGH);
            delay(10);
            digitalWrite(Y_STP,LOW);
            yPos=yPos+yStepAdd;
            delay(10);
          }

          //break;
          numstep2=0;

        }
        yPos=yPos+yStepAdd;
      }
      if(counter <= numstep3){
        digitalWrite(Z_STP,LOW);
        if (!digitalRead(LIMITZ) && engageLimits==1) {


          if(ble.isConnected()) {
            ble.write("hit z limit");
          }
          //Serial.println("hit z limit");
          //Serial.println(zPos);
          if (zPos<=5000) {dir3=1; zStepAdd=1;} else { dir3=0; zStepAdd=-1;}
            digitalWrite(Z_DIR,dir3);

          while(!digitalRead(LIMITZ)){
            digitalWrite(Z_STP,HIGH);
            delay(10);
            digitalWrite(Z_STP,LOW);
            zPos=zPos+zStepAdd;
            delay(10);

          }

          for (int i=0; i<10;i++) {
            digitalWrite(Z_STP,HIGH);
            delay(10);
            digitalWrite(Z_STP,LOW);
            zPos=zPos+zStepAdd;
            delay(10);
          }

          //break;
          numstep3=0;

        }
        zPos=zPos+zStepAdd;
      }

      delayMicroseconds(DELAYTIME);
    }
    digitalWrite(X_EN,HIGH);
    digitalWrite(Y_EN,HIGH);
    digitalWrite(Z_EN,HIGH);
}

void homing_sequence() {
  //homing routine. homes and then moves out 1000 for x and y and 1500 for x
           /*if (!digitalRead(LIMITX) {
              digitalWrite(X_DIR,0);

              for (int j=0; j<2;j++) {
                for (int i=0; i<200;i++) {
                  digitalWrite(X_STP,HIGH);
                  delay(10);
                  digitalWrite(X_STP,LOW);
                  xPos=xPos+xStepAdd;
                  delay(10);
                }
                if (!digitalRead(LIMITX) break; else digitalWrite(X_DIR,1);

              }
           }*/
       //step_motor_fried(100000,0,0,0,0,0); //y
       //step_motor_fried(0,0,100000,1,0,0); //x
       //step_motor_fried(10000,1,10000,0,15000,1);
       xPos=4999;
       yPos=5001;
       zPos=4999;

       step_motor_fried(100000,0,100000,1,100000,0); //z
       //step_motor_fried(0,0,0,0,100000,0); //z
       //step_motor_fried(0,0,100000,1,0,0); //x
       //step_motor_fried(100000,0,0,0,0,0); //y
       yPos=30000;
       xPos=0;
       zPos=0;
       step_motor_fried(1000,1,1000,0,1500,1); //z

       //step_motor_fried(0,0,0,0,1500,1); //z
       //step_motor_fried(1000,1,0,0,0,0); //y
       //step_motor_fried(0,0,1000,0,0,0); //actually x but uses ypos /ystepadd


       //step_motor_fried(0,0,0,0,15000,1);
       //step_motor_fried(10000,1,0,0,0,0);
       //step_motor_fried(0,0,10000,0,0,0); //note that the x is reversed
       //zPos=15000;
       //xPos=whatever the limit val is;
       //yPos=10000;
       //after home z range is +-7000, y range is +-4000, and x range is +-4000
}