This is an Android application developed for Auburn University Spring 2024 Department of Electrical and Computer Engineering capstone senior design.

Our project is called StarSync and is meant to provide a user interface for an automated dobsonian telescope. This application will give the user the ability to 
select one of the deep space objects available on the Hipparcos Star catalogue and calculate both local alt-az coordinates for this object as well as a 
local magnetic field vector. Both of these calculations will be based off of the phones GNSS location as well as the current time. The user will then have the 
ability to transmit one of four one letter commands to the telescope's microcontroller via Bluetooth. The four commands are:
  1) Pointing (P) - This command is followed by both the alt-az coordinates as well as the magnetic field vector, this will initiate the tracking process.
  2) Health (H) - This command will prompt the microcontroller to transmit any error signals it has generated, to be viewed by the user.
  3) Calibration (C) - This command till prompt the microcontroller to recalibrate all of the electrical components of the telescope:
       (magnetometers, accelerometers, stepper motors, encoders)
  4) Standby (S) - This command will prompt the microcontroller to pause all current operations.

This app is written in Kotlin, with a Python file used for accessing the Hipparcos Star Catalogue and performing all necessary calculations related to both the 
alt-az coordinates as well as the magnetic field vector. The Python library SkyField was used for calculations. 

Developed by Steven Perry and Brendan Riley.
