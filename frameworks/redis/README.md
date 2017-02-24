# Cheet sheat

## Initial setup

Get DCOS CLI master build for 'task exec' support:
- [OSX](https://teamcity.mesosphere.io/repository/download/DcosIo_DcosCli_OsX_MacBinary/568526:id/dcos) (guest:guest)
- [Linux](https://teamcity.mesosphere.io/repository/download/DcosIo_DcosCli_Linux_Binary/568667:id/dcos) (guest:guest)

Add SSH key for cluster:
```
chmod 400 <cluster-ssh-key>
ssh-add <cluster-ssh-key>
```

Configure CLI with cluster:
```
dcos config set core.dcos_url <cluster-url>
dcos auth login
```

## Dev cycle

```
# uninstall previous package (if any):
dcos package uninstall redis

# delete data (from mesos master, see 'node ssh' command below):
docker run mesosphere/janitor /janitor.py -r redis-role -p redis-principal -z dcos-service-redis

# build and install package:
S3_BUCKET=yourbucket S3_DIR_PATH=yourpath/in/bucket ./build.sh aws # from dcos-commons/frameworks/redis/
<run the three commands printed at end of above command>
```

## Other operations

- View marathon: `http://<CLUSTER>/marathon`
- View mesos: `http://<CLUSTER>/mesos`

- SSH to mesos master (for running janitor.py):
```
dcos node ssh --master-proxy --leader
```

- List agents, SSH to agent, find redis-related container files on agent:
```
dcos node
dcos node ssh --master-proxy --mesos-id=<UUID> # UUID should end in "-S#"
find /var/lib/mesos -iname 'redislabs'
```

- List tasks, run command in task env:
```
dcos task
dcos task exec <task name, eg redis-0-server> <command>
```

- Connect to `redis-0` dashboard:
1. Create this `repoxy` task via `/marathon` (will change once proxylite has https support):
```
{
  "id": "/repoxy",
  "cpus": 1,
  "acceptedResourceRoles": [
      "slave_public"
  ],
  "instances": 1,
  "mem": 512,
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "mesosphere/repoxy:1.0.1a"
    },
    "volumes": [
      {
        "containerPath": "/opt/mesosphere",
        "hostPath": "/opt/mesosphere",
        "mode": "RO"
      }
    ]
  },
  "portDefinitions": [
    {
      "port": 0,
      "protocol": "tcp"
    },
    {
      "port": 0,
      "protocol": "tcp"
    }
  ],
  "cmd": "/proxyfiles/bin/start redis $PORT0",
  "env": {
    "PROXY_ENDPOINT_0": "My Cool Dashboard,redis-0-server,mesos,8443,/ui,/"
  }
}
```
2. Once the `repoxy` task is running, look at its `stdout` (via sandbox on `/mesos` or task info on `/marathon`) to get the Redis dashboard URL
3. Note that the persistent volume (survives container restarts) should be at `/mnt/mesos/sandbox/redis-volume`

## Useful files

- [DOC: Developer Guide](../../docs/pages/dev-guide/developer-guide.md)
- [svc.yml: Service spec](src/main/dist/svc.yml)
- [universe: Package metadata](universe/)
