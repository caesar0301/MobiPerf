#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Service to collect and visualize mobile network performance data."""

__author__ = 'mdw@google.com (Matt Welsh)'

# pylint: disable-msg=C6205
try:
  # This is expected to fail on the local server.
  from google.appengine.dist import use_library  # pylint: disable-msg=C6204
except ImportError:
  pass
else:
  # We could use newer version of Django, but 1.2 is the highest version that
  # Appengine provides (as of May 2011).
  use_library('django', '1.2')

#from google.appengine.ext.webapp import Request
#from google.appengine.ext.webapp import RequestHandler
#from google.appengine.ext.webapp import Response
#from google.appengine.ext.webapp import template
from google.appengine.ext.webapp.util import run_wsgi_app

# pylint: disable-msg=W0611
from gspeedometer import wsgi
from gspeedometer.controllers import checkin
from gspeedometer.controllers import device
from gspeedometer.controllers import home
from gspeedometer.controllers import measurement
from gspeedometer.controllers import schedule

import routes

m = routes.Mapper()
m.connect('/',
          controller='home:Home',
          action='Dashboard')

m.connect('/checkin',
          controller='checkin:Checkin',
          action='Checkin')

m.connect('/device/view',
          controller='device:Device',
          action='DeviceDetail')

m.connect('/postmeasurement',
          controller='measurement:Measurement',
          action='PostMeasurement')

m.connect('/measurement/view',
          controller='measurement:Measurement',
          action='MeasurementDetail')

m.connect('/schedule/add',
          controller='schedule:Schedule',
          action='Add')

m.connect('/schedule/delete',
          controller='schedule:Schedule',
          action='Delete')

application = wsgi.WSGIApplication(m, debug=True)


def main():
  run_wsgi_app(application)


if __name__ == '__main__':
  main()