# Copyright 2017 The Authors. All Rights Reserved.
# See the AUTHORS file distributed with
# this work for additional information regarding The Authors.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#     http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==============================================================================

def msg_map(r, func=str, conn={}):
    from collections import namedtuple
    from rosbag.bag import _get_message_type
    if r[1]['header']['op'] == 2 and r[1]['header']['conn'] == conn['header']['conn']:
        c = conn['data']
        c['datatype'] = str(c['type'])
        c['msg_def'] = str(c['message_definition'])
        c['md5sum'] = str(c['md5sum'])
        c = namedtuple('GenericDict', c.keys())(**c)
        msg_type = _get_message_type(c)
        msg = msg_type()
        msg.deserialize(r[1]['data'])
        yield func(msg)
