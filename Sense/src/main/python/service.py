from flask import Flask, Response
from sense_hat import SenseHat


sense = SenseHat()
sense.set_imu_config(True, True, True)
app = Flask(__name__)


@app.route('/')
def all_sensors():
    return "Hello, world!"


@app.route('/humidity')
def humidity():
    return "Hello, world!"


@app.route('/pressure')
def pressure():
    return "Hello, world!"


@app.route('/temperature')
def temperature():
    return "Hello, world!"


@app.route('/orientation')
def orientation():
    orientation_degrees = sense.get_orientation_degrees()
    return "p: {pitch}, r: {roll}, y: {yaw}".format(**orientation_degrees)


@app.route('/accelerometer')
def accelerometer():
    return "Hello, world!"


@app.route('/magnetometer')
def magnetometer():
    return "Hello, world!"


@app.route('/gyroscope')
def gyroscope():
    return "Hello, world!"


if __name__ == '__main__':
    app.run(host='0.0.0.0', port='80', debug=True)