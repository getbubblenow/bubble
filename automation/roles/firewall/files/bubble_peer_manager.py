#!/usr/bin/python3
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import json
import logging
import os
import sys
import time
import subprocess

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

EMPTY_PEERS = {'peers': [], 'ports': []}


class PeerPort(object):
    def __init__(self, port):
        if ':' in port:
            self.proto = port[0:port.index(':')]
            self.port = port[port.index(':') + 1:]
        else:
            self.proto = 'tcp'
            self.port = port

    def __str__(self):
        return self.proto + ':' + self.port


def find_peers(port):
    out = subprocess.run(['iptables', '-vnL', 'INPUT'],
                         stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE)
    peers = []
    for line in out.stdout.decode('utf-8').split('\n'):
        line = line.strip()
        if len(line) == 0 or line.startswith('Chain ') or line.startswith('pkts '):
            continue
        for parts in line.split(' '):
            packets = parts[0]
            bytes = parts[1]
            target = parts[2]
            proto = parts[3]
            if proto != port.proto:
                continue
            opt = parts[4]
            iface_in = parts[5]
            iface_out = parts[6]
            source = parts[7]
            if source == '0.0.0.0/0':
                continue
            dest = parts[8]
            if parts[9] != port.proto:
                continue
            if parts[10].startswith('dpt:'):
                dest_port = int(parts[10][len('dpt:'):])
                if dest_port == port.port:
                    peers.append(source)
    return peers


def add_peers(peers, port):
    out = subprocess.run(['iptables', '-vnL', 'INPUT'],
                         stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE)
    lines = out.stdout.decode('utf-8').split('\n')
    insert_at = len(lines) - 2
    if insert_at < 2:
        raise ValueError('add_peers: insert_at was < 2: '+str(insert_at))
    for peer in peers:
        logger.info("add_peers: alllowing peer: " + peer + " on port " + port)
        out = subprocess.run(['iptables', '-I', 'INPUT', str(insert_at),
                              '-p', port.proto, '-s', peer + '/32',
                              '--dport', port.port, '-j', 'ACCEPT'])
        logger.info("add_peers: allowed peer: " + peer + " on port " + port)


def remove_peers(peers, port):
    for peer in peers:
        remove_peer(peer, port)


def remove_peer(peer, port):
    out = subprocess.run(['iptables', '-vnL', 'INPUT'],
                         stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE)
    index = 0
    for line in out.stdout.decode('utf-8').split('\n'):
        line = line.strip()
        if len(line) == 0 or line.startswith('Chain ') or line.startswith('pkts '):
            continue
        index = index + 1
        for parts in line.split(' '):
            packets = parts[0]
            bytes = parts[1]
            target = parts[2]
            proto = parts[3]
            if proto != port.proto:
                continue
            opt = parts[4]
            iface_in = parts[5]
            iface_out = parts[6]
            source = parts[7]
            if not source.startswith(peer+'/32'):
                continue
            dest = parts[8]
            if parts[9] != port.proto:
                continue
            if parts[10].startswith('dpt:'):
                dest_port = int(parts[10][len('dpt:'):])
                if dest_port == port.port:
                    logger.info("remove_peer: removing peer: " + peer + " on port " + port)
                    out = subprocess.run(['iptables', '-D', 'INPUT', str(index)],
                                         stdout=subprocess.PIPE,
                                         stderr=subprocess.PIPE)
                    return True
    return False


class BubblePeers(object):

    def __init__(self, peer_path, self_path):
        self.peer_path = peer_path
        if os.path.exists(peer_path):
            self.last_modified = os.path.getmtime(self.peer_path)
        else:
            self.last_modified = 0

        self.last_update = None
        self.peers = []
        self.ports = []

        self.self_path = self_path
        self.self_node = {}

    def load_peers(self):
        if os.path.exists(self.peer_path):
            with open(self.peer_path) as f:
                val = json.load(f)
        else:
            val = EMPTY_PEERS
        self.peers = val['peers']
        self.ports = []
        for port in val['ports']:
            self.ports.append(PeerPort(port))

    def load_self(self):
        if os.path.exists(self.self_path):
            with open(self.self_path) as f:
                self.self_node = json.load(f)

    def monitor(self):
        self.load_peers()
        self.load_self()
        if os.path.exists(self.peer_path):
            self.last_modified = os.path.getmtime(self.peer_path)
            if self.last_update is None or self.last_update < self.last_modified:
                self.load_peers()
                for port in self.ports:
                    peers_on_port = find_peers(port)
                    peers_to_remove = []
                    peers_to_add = []
                    for peer in peers_on_port:
                        if peer not in self.peers:
                            peers_to_remove.append(peer)
                    for peer in self.peers:
                        if peer not in peers_on_port:
                            peers_to_add.append(peer)
                    remove_peers(peers_to_remove, port)
                    add_peers(peers_to_add, port)


if __name__ == "__main__":
    peers = BubblePeers(sys.argv[1], sys.argv[2])
    interval = int(sys.argv[3])
    try:
        while True:
            peers.monitor()
            time.sleep(interval)
    except Exception as e:
        logger.error("Unexpected error: " + repr(e))
