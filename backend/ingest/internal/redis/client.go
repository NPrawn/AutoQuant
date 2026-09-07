package redis

import goredis "github.com/redis/go-redis/v9"

func NewClient(address string) *goredis.Client {
	return goredis.NewClient(&goredis.Options{Addr: address})
}
